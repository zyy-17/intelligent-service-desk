"""FastAPI AI 服务主入口。

提供智能客服辅助能力：智能问答、工单摘要、分类推荐、知识检索、回复草稿。
Java 业务后端作为主控方，通过 HTTP 调用本服务。
"""
import logging
import uuid
import time
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from .config import settings
from .llm import chat_completion, chat_completion_stream, is_configured, LLMError
from .retrieval import search_knowledge, build_context, SYSTEM_PROMPT
from .sessions import store
from .schemas import (
    ChatRequest,
    ChatResponse,
    SummaryRequest,
    SummaryResponse,
    ClassifyRequest,
    ClassifyResponse,
    DraftRequest,
    DraftResponse,
    SearchRequest,
    SearchResponse,
    SessionCreateRequest,
    SessionDetail,
    SessionSummary,
    ErrorDetail,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("ai-service")

app = FastAPI(
    title="Intelligent Service Desk AI Service",
    description="智能客服与工单管理平台的 AI 辅助服务",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _task_id() -> str:
    return uuid.uuid4().hex


@app.get("/health")
def health():
    return {"status": "ok", "model_configured": is_configured(), "redis": store.redis_available}


@app.post("/api/ai/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    """智能问答：优先云端模型（RAG），失败降级为知识检索。支持会话记录。"""
    task_id = _task_id()
    question = req.question.strip()

    # 1. 知识检索
    hits = search_knowledge(question, req.knowledge)

    # 2. 尝试云端模型
    if is_configured():
        try:
            ctx = build_context(hits)
            history = _recent_history(req.session_id, req.user_id)
            messages = [{"role": "system", "content": SYSTEM_PROMPT}]
            messages.extend(history)
            messages.append({
                "role": "user",
                "content": f"知识库参考：\n{ctx or '（无相关知识）'}\n\n用户问题：{question}",
            })
            answer = chat_completion(messages)
            source = hits[0].title if hits else "模型生成"
            sid = _record_session(req, question, answer)
            return ChatResponse(
                task_id=task_id,
                answer=answer,
                source=source,
                references=hits,
                fallback=False,
                model=settings.ai_model,
                session_id=sid,
            )
        except LLMError as e:
            logger.warning("云端模型调用失败，降级为知识检索：%s", e.message)

    # 3. 降级：知识检索
    answer = _fallback_answer(question, hits)
    sid = _record_session(req, question, answer)
    return ChatResponse(
        task_id=task_id,
        answer=answer,
        source=hits[0].title if hits else "未找到匹配知识",
        references=hits,
        fallback=True,
        session_id=sid,
    )


@app.post("/api/ai/chat/stream")
def chat_stream(req: ChatRequest):
    """流式智能问答：以 SSE 形式逐段返回模型回答，支持会话记录。"""
    question = req.question.strip()

    def _gen():
        start = time.time()
        hits = search_knowledge(question, req.knowledge)
        # 先发送会话 ID（若已存在）供前端绑定
        try:
            ctx = build_context(hits)
            history = _recent_history(req.session_id, req.user_id)
            messages = [{"role": "system", "content": SYSTEM_PROMPT}]
            messages.extend(history)
            messages.append({
                "role": "user",
                "content": f"知识库参考：\n{ctx or '（无相关知识）'}\n\n用户问题：{question}",
            })

            if is_configured():
                answer_parts = []
                for piece in chat_completion_stream(messages):
                    answer_parts.append(piece)
                    yield _sse("delta", piece)
                answer = "".join(answer_parts)
                source = hits[0].title if hits else "模型生成"
                fallback = False
            else:
                raise LLMError("AI_NOT_CONFIGURED", "未配置云端模型")
        except LLMError as e:
            logger.warning("流式调用失败，降级为知识检索：%s", e.message)
            answer = _fallback_answer(question, hits)
            source = hits[0].title if hits else "未找到匹配知识"
            fallback = True
            yield _sse("delta", answer)

        duration_ms = int((time.time() - start) * 1000)
        sid = _record_session(req, question, answer)
        yield _sse("done", "", {
            "source": source,
            "fallback": fallback,
            "model": settings.ai_model if not fallback else None,
            "session_id": sid,
            "duration_ms": duration_ms,
        })

    return StreamingResponse(_gen(), media_type="text/event-stream")


def _sse(event: str, data: str, meta: dict | None = None):
    """构造单条 SSE 消息。"""
    import json as _json
    payload = {"event": event, "data": data}
    if meta:
        payload.update(meta)
    return f"data: {_json.dumps(payload, ensure_ascii=False)}\n\n"


def _recent_history(session_id: Optional[str], user_id: Optional[int]):
    """取会话最近若干条消息作为多轮上下文。"""
    if not session_id or not user_id:
        return []
    try:
        data = store.get(session_id, user_id)
        if not data:
            return []
        history = []
        for m in data["messages"][-8:]:
            history.append({"role": m["role"], "content": m["content"]})
        return history
    except Exception as e:
        logger.warning("读取会话历史失败：%s", e)
        return []


def _record_session(req: ChatRequest, question: str, answer: str) -> Optional[str]:
    """把一轮问答写入会话（会话不存在时自动创建），返回会话 ID。"""
    if not req.user_id:
        return None
    try:
        sid = req.session_id
        if sid:
            ok = store.append_message(sid, req.user_id, "user", question)
            if not ok:
                sid = None  # 会话不存在（如已被删除），自动新建
        if not sid:
            created = store.create(req.user_id)
            sid = created["session_id"]
        store.append_message(sid, req.user_id, "user", question)
        store.rename_if_first(sid, req.user_id)
        store.append_message(sid, req.user_id, "assistant", answer)
        return sid
    except Exception as e:
        logger.warning("会话写入失败：%s", e)
        return None


# ===== 会话管理 =====

@app.post("/api/ai/sessions")
def create_session(req: SessionCreateRequest):
    return store.create(req.user_id, req.title)


@app.get("/api/ai/sessions", response_model=List[SessionSummary])
def list_sessions(user_id: int, limit: int = 50):
    return store.list(user_id, limit)


@app.get("/api/ai/sessions/{session_id}", response_model=SessionDetail)
def get_session(session_id: str, user_id: int):
    data = store.get(session_id, user_id)
    if data is None:
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")
    return data


@app.delete("/api/ai/sessions/{session_id}")
def delete_session(session_id: str, user_id: int):
    if not store.delete(session_id, user_id):
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")
    return {"deleted": True}


@app.post("/api/ai/summarize", response_model=SummaryResponse)
def summarize(req: SummaryRequest):
    """工单摘要。"""
    task_id = _task_id()
    if not is_configured():
        return SummaryResponse(
            task_id=task_id,
            summary=f"{req.title}：{req.description[:100]}",
            error=ErrorDetail(code="AI_NOT_CONFIGURED", message="未配置云端模型"),
        )
    try:
        messages = [
            {"role": "system", "content": "你是客服工单助手，请用一句话概括工单的核心问题，不超过 50 字。"},
            {"role": "user", "content": f"主题：{req.title}\n描述：{req.description}"},
        ]
        summary = chat_completion(messages, temperature=0.2)
        return SummaryResponse(task_id=task_id, summary=summary, model=settings.ai_model)
    except LLMError as e:
        return SummaryResponse(
            task_id=task_id,
            summary=f"{req.title}：{req.description[:100]}",
            error=ErrorDetail(code=e.code, message=e.message),
        )


@app.post("/api/ai/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    """分类推荐。"""
    task_id = _task_id()
    if not req.categories:
        return ClassifyResponse(task_id=task_id, confidence=0.0)

    # 无模型时用关键词匹配兜底
    if not is_configured():
        matched = _keyword_classify(req.title + req.description, req.categories)
        if matched is None:
            return ClassifyResponse(task_id=task_id, confidence=0.0)
        return ClassifyResponse(
            task_id=task_id,
            category_id=matched.id,
            category_name=matched.name,
            confidence=0.6,
        )

    names = "、".join(f"{c.id}:{c.name}" for c in req.categories)
    try:
        messages = [
            {"role": "system", "content": "你是客服工单分类助手。请从给定分类中选择最合适的一个，只返回分类 ID（数字）。"},
            {"role": "user", "content": f"候选分类：{names}\n\n主题：{req.title}\n描述：{req.description}"},
        ]
        raw = chat_completion(messages, temperature=0.0)
        # 从返回中提取第一个数字
        import re as _re
        m = _re.search(r"\d+", raw)
        if not m:
            raise LLMError("AI_PARSE_ERROR", "分类结果无有效 ID")
        cid = int(m.group())
        for c in req.categories:
            if c.id == cid:
                return ClassifyResponse(
                    task_id=task_id,
                    category_id=c.id,
                    category_name=c.name,
                    confidence=0.9,
                    model=settings.ai_model,
                )
        return ClassifyResponse(task_id=task_id, confidence=0.0, model=settings.ai_model)
    except LLMError as e:
        matched = _keyword_classify(req.title + req.description, req.categories)
        if matched is None:
            return ClassifyResponse(task_id=task_id, confidence=0.0, error=ErrorDetail(code=e.code, message=e.message))
        return ClassifyResponse(
            task_id=task_id,
            category_id=matched.id,
            category_name=matched.name,
            confidence=0.6,
            error=ErrorDetail(code=e.code, message=e.message),
        )


@app.post("/api/ai/draft", response_model=DraftResponse)
def draft(req: DraftRequest):
    """回复草稿。"""
    task_id = _task_id()
    if not is_configured():
        return DraftResponse(
            task_id=task_id,
            draft="您好，我们已收到您的工单，正在为您处理，请稍候。",
            error=ErrorDetail(code="AI_NOT_CONFIGURED", message="未配置云端模型"),
        )
    try:
        ctx = build_context(req.knowledge)
        messages = [
            {"role": "system", "content": "你是客服人员助手，请根据工单内容和知识库，起草一段专业、礼貌的中文回复。"},
            {"role": "user", "content": f"知识库参考：\n{ctx or '（无）'}\n\n工单主题：{req.title}\n问题描述：{req.description}"},
        ]
        result = chat_completion(messages, temperature=0.5)
        return DraftResponse(task_id=task_id, draft=result, model=settings.ai_model)
    except LLMError as e:
        return DraftResponse(
            task_id=task_id,
            draft="您好，我们已收到您的工单，正在为您处理，请稍候。",
            error=ErrorDetail(code=e.code, message=e.message),
        )


@app.post("/api/ai/search", response_model=SearchResponse)
def search(req: SearchRequest):
    """知识检索（纯本地，不依赖模型）。"""
    task_id = _task_id()
    results = search_knowledge(req.query, req.knowledge)
    return SearchResponse(task_id=task_id, results=results)


def _keyword_classify(text: str, categories) -> object | None:
    """关键词兜底分类。"""
    from .schemas import CategoryOption
    kw_map = {
        "账户": ["账户", "登录", "密码", "账号", "验证"],
        "产品咨询": ["产品", "功能", "咨询", "介绍", "套餐"],
        "技术故障": ["故障", "报错", "闪退", "卡", "超时", "乱码", "异常"],
        "账单与支付": ["退款", "账单", "发票", "支付", "续费", "金额"],
        "意见建议": ["建议", "希望", "增加", "意见"],
    }
    for c in categories:
        c: CategoryOption
        kws = kw_map.get(c.name, [])
        for kw in kws:
            if kw in text:
                return c
    return None


def _fallback_answer(question: str, hits) -> str:
    if not hits:
        return (
            f"抱歉，未在知识库中找到与「{question}」直接相关的答案。\n\n"
            "建议您：\n1. 换个关键词重新描述问题；\n2. 提交工单，由人工客服为您处理；\n3. 查看知识库热门文章获取帮助。"
        )
    top = hits[0]
    body = (top.body or "").strip()
    if len(body) > 800:
        body = body[:800] + "…"
    parts = [f"根据知识库文章《{top.title}》，为您解答：\n\n{body}"]
    if len(hits) > 1:
        more = "、".join(f"《{h.title}》" for h in hits[1:4])
        parts.append(f"\n\n更多相关内容：{more}")
    parts.append("\n\n（当前为知识库检索结果，配置云端模型后可获得更智能的回答。）")
    return "".join(parts)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=False)
