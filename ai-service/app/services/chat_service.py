"""智能问答编排：知识检索 → 云端模型（RAG）→ 检索降级 → 会话落库。

对外只暴露两个入口：answer()（一次性）与 answer_stream()（SSE 流式）。
"""
import logging
import time
import uuid
from typing import Iterator, List, Tuple

from ..core.config import settings
from ..llm import LLMError, chat_completion, chat_completion_stream, is_configured
from ..prompts import build_chat_messages
from ..retrieval import build_context, fallback_answer, search_knowledge
from ..schemas import ChatRequest, ChatResponse, KnowledgeItem
from ..utils.sse import encode
from .session_service import recent_history, record_turn

logger = logging.getLogger("ai-service.chat")


def new_task_id() -> str:
    """生成唯一任务标识。"""
    return uuid.uuid4().hex


def _prepare(req: ChatRequest) -> Tuple[List[KnowledgeItem], List[dict]]:
    """检索知识库并组装本轮完整 messages。"""
    hits = search_knowledge(req.question.strip(), req.knowledge, settings.knowledge_top_k)
    context = build_context(hits)
    history = recent_history(req.session_id, req.user_id)
    messages = build_chat_messages(req.question.strip(), context, history)
    return hits, messages


def _source_of(hits: List[KnowledgeItem], fallback: bool) -> str:
    """计算答案来源标签。"""
    if hits:
        return hits[0].title
    return "未找到匹配知识" if fallback else "模型生成"


def answer(req: ChatRequest) -> ChatResponse:
    """一次性回答：模型可用则走 RAG，否则用知识检索结果作答。"""
    task_id = new_task_id()
    question = req.question.strip()
    hits, messages = _prepare(req)

    if is_configured():
        try:
            text = chat_completion(messages)
            return ChatResponse(
                task_id=task_id,
                answer=text,
                source=_source_of(hits, fallback=False),
                references=hits,
                fallback=False,
                model=settings.ai_model,
                session_id=record_turn(req, question, text),
            )
        except LLMError as e:
            logger.warning("云端模型调用失败，降级为知识检索：%s", e.message)

    text = fallback_answer(question, hits)
    return ChatResponse(
        task_id=task_id,
        answer=text,
        source=_source_of(hits, fallback=True),
        references=hits,
        fallback=True,
        session_id=record_turn(req, question, text),
    )


def answer_stream(req: ChatRequest) -> Iterator[str]:
    """流式回答：逐条产出 SSE 消息，最后一条 done 携带来源与耗时。"""
    start = time.time()
    question = req.question.strip()
    hits, messages = _prepare(req)
    fallback = False

    if is_configured():
        try:
            parts: List[str] = []
            for piece in chat_completion_stream(messages):
                parts.append(piece)
                yield encode("delta", piece)
            text = "".join(parts)
        except LLMError as e:
            logger.warning("流式调用失败，降级为知识检索：%s", e.message)
            text = fallback_answer(question, hits)
            fallback = True
            yield encode("delta", text)
    else:
        text = fallback_answer(question, hits)
        fallback = True
        yield encode("delta", text)

    duration_ms = int((time.time() - start) * 1000)
    yield encode(
        "done",
        "",
        source=_source_of(hits, fallback=fallback),
        fallback=fallback,
        model=None if fallback else settings.ai_model,
        session_id=record_turn(req, question, text),
        duration_ms=duration_ms,
    )
