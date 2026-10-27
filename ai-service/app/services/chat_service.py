"""智能问答编排：向量检索 → 云端模型（RAG）→ 检索降级 → 会话落库。

对外只暴露两个入口：answer()（一次性）与 answer_stream()（SSE 流式）。
回答必须基于检索到的已发布知识，检索不可靠时明确告知并引导转人工。
"""
import logging
import time
import uuid
from typing import Iterator, List, Optional, Tuple

from ..core.config import settings
from ..llm import LLMError, generate, has_endpoint, stream as model_stream
from ..prompts import build_chat_messages
from ..retrieval import build_context, fallback_answer, search_knowledge, to_references
from ..schemas import ChatRequest, ChatResponse, KnowledgeBrief, KnowledgeItem, TicketBrief
from ..utils.labels import priority_label, status_label
from ..utils.sse import encode
from .session_service import recent_history, record_turn

logger = logging.getLogger("ai-service.chat")

# 命中这些说法时，无模型也优先用工单数据回答
TICKET_KEYWORDS = ("我的工单", "工单进度", "工单状态", "我的问题", "处理进度", "提交的工单", "查工单", "工单怎么样")

# 模型提示词里知识库目录的最大条数
CATALOG_MAX = 40

# 云端与 Ollama 都不可用时的来源标签，写进 done 事件与会话记录供前端展示
LOCAL_FALLBACK_LABEL = "本地知识库检索"


def new_task_id() -> str:
    """生成唯一任务标识。"""
    return uuid.uuid4().hex


def _catalog_of(req: ChatRequest) -> List[KnowledgeBrief]:
    """知识库目录：优先用 Java 下发的目录，缺失时由全量文档派生。"""
    if req.knowledge_catalog:
        return req.knowledge_catalog
    return [
        KnowledgeBrief(id=d.id, title=d.title, summary=d.summary,
                       keywords=d.keywords, category_name=d.category_name)
        for d in (req.knowledge_docs or [])[:CATALOG_MAX]
    ]


def _retrieve(req: ChatRequest) -> List[KnowledgeItem]:
    """向量检索：优先在已发布知识全量文档中检索，兼容旧版预检索结果。"""
    docs = req.knowledge_docs or req.knowledge
    hits = search_knowledge(req.question.strip(), docs)
    if hits:
        logger.info("[检索] 问题=%s 命中=%d 最高分=%.3f 首篇=%s",
                    req.question.strip()[:40], len(hits), hits[0].score or 0, hits[0].title)
    else:
        logger.info("[检索] 问题=%s 未命中（阈值 %.2f，语料 %d 篇）",
                    req.question.strip()[:40], settings.retrieval_min_score, len(docs))
    return hits


def _prepare(req: ChatRequest) -> Tuple[List[KnowledgeItem], List[dict]]:
    """检索知识库并组装本轮完整 messages（含用户身份、工单与知识库目录）。"""
    hits = _retrieve(req)
    messages = build_chat_messages(
        req.question.strip(),
        build_context(hits),
        recent_history(req.session_id, req.user_id),
        profile=req.user_profile,
        tickets=req.tickets,
        catalog=_catalog_of(req),
        hits=hits,
    )
    return hits, messages


def _source_of(hits: List[KnowledgeItem], fallback: bool) -> str:
    """计算答案来源标签。"""
    if hits:
        return hits[0].title
    return "未找到可靠知识依据" if fallback else "模型生成（无知识依据）"


def _fallback_text(question: str, hits: List[KnowledgeItem], req: ChatRequest) -> str:
    """无模型时的降级回答：问工单就直接用工单数据，否则用知识库检索结果。"""
    if req.tickets and any(kw in question for kw in TICKET_KEYWORDS):
        return _tickets_answer(req.tickets)
    return fallback_answer(question, hits)


def _tickets_answer(tickets: List[TicketBrief]) -> str:
    """用真实工单数据拼出可直接阅读的回答（模型不可用时的兜底）。"""
    lines = [f"您目前共有 {len(tickets)} 条近期工单："]
    for idx, t in enumerate(tickets, 1):
        meta = " · ".join(x for x in [status_label(t.status), priority_label(t.priority), t.created_at] if x)
        lines.append(f"{idx}. #{t.ticket_no} {t.title}" + (f"（{meta}）" if meta else ""))
    lines.append("\n需要查看某条工单的完整处理记录，可以在「我的工单」页面打开对应工单。")
    return "\n".join(lines)


def _ticket_intent(question: str, req: ChatRequest) -> bool:
    """是否在问自己的工单：有工单数据且问题命中工单关键词，此时依据是系统数据而非知识库。"""
    return bool(req.tickets) and any(kw in question for kw in TICKET_KEYWORDS)


def answer(req: ChatRequest) -> ChatResponse:
    """一次性回答：云端/Ollama 可用则走 RAG，全部失败才用知识检索兜底。"""
    task_id = new_task_id()
    question = req.question.strip()
    hits, messages = _prepare(req)
    references = to_references(hits)
    # 依据真实工单数据作答也算「有依据」，避免误提示知识库无覆盖
    grounded = bool(hits) or _ticket_intent(question, req)

    if has_endpoint():
        try:
            text, endpoint = generate(messages)
            source = _source_of(hits, fallback=False)
            return ChatResponse(
                task_id=task_id,
                answer=text,
                source=source,
                references=references,
                grounded=grounded,
                fallback=False,
                model=endpoint.label,
                session_id=record_turn(req, question, text, references, grounded, source, endpoint.label),
            )
        except LLMError as e:
            logger.warning("模型全部失败，降级为知识检索：%s", e.message)

    text = _fallback_text(question, hits, req)
    source = _source_of(hits, fallback=True)
    return ChatResponse(
        task_id=task_id,
        answer=text,
        source=source,
        references=references,
        grounded=grounded,
        fallback=True,
        model=LOCAL_FALLBACK_LABEL,
        session_id=record_turn(req, question, text, references, grounded, source, LOCAL_FALLBACK_LABEL),
    )


def answer_stream(req: ChatRequest) -> Iterator[str]:
    """流式回答：逐条产出 SSE 消息，最后一条 done 携带来源、引用与耗时。"""
    start = time.time()
    question = req.question.strip()
    hits, messages = _prepare(req)
    references = to_references(hits)
    grounded = bool(hits) or _ticket_intent(question, req)
    fallback = False
    used = {}

    def on_used(endpoint) -> None:
        """记录实际用上的端点，写进 done 事件的 model 字段。"""
        used["label"] = endpoint.label

    parts: List[str] = []
    if has_endpoint():
        try:
            for piece in model_stream(messages, on_used=on_used):
                parts.append(piece)
                yield encode("delta", piece)
            text = "".join(parts)
        except LLMError as e:
            logger.warning("模型全部失败，降级为知识检索：%s", e.message)
            fallback = True
            if parts:
                # 已经输出了一部分：保留现有文本，不再追加兜底，避免前端把两段回答拼在一起
                text = "".join(parts)
            else:
                text = _fallback_text(question, hits, req)
                yield encode("delta", text)
    else:
        text = _fallback_text(question, hits, req)
        fallback = True
        yield encode("delta", text)

    duration_ms = int((time.time() - start) * 1000)
    source = _source_of(hits, fallback=fallback)
    model = used.get("label") or LOCAL_FALLBACK_LABEL
    yield encode(
        "done",
        "",
        source=source,
        fallback=fallback,
        grounded=grounded,
        references=references,
        model=model,
        session_id=record_turn(req, question, text, references, grounded, source, model),
        duration_ms=duration_ms,
    )
