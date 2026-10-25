"""FastAPI AI 服务主入口。

提供智能客服辅助能力：智能问答、工单摘要、分类推荐、知识检索、回复草稿。
Java 业务后端作为主控方，通过 HTTP 调用本服务。
"""
import logging
import uuid
import time

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import settings
from .llm import chat_completion, is_configured, LLMError
from .retrieval import search_knowledge, build_context, SYSTEM_PROMPT
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
    return {"status": "ok", "model_configured": is_configured()}


@app.post("/api/ai/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    """智能问答：优先云端模型（RAG），失败降级为知识检索。"""
    task_id = _task_id()
    question = req.question.strip()

    # 1. 知识检索
    hits = search_knowledge(question, req.knowledge)

    # 2. 尝试云端模型
    if is_configured():
        try:
            ctx = build_context(hits)
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"知识库参考：\n{ctx or '（无相关知识）'}\n\n用户问题：{question}"},
            ]
            answer = chat_completion(messages)
            source = hits[0].title if hits else "模型生成"
            return ChatResponse(
                task_id=task_id,
                answer=answer,
                source=source,
                references=hits,
                fallback=False,
                model=settings.ai_model,
            )
        except LLMError as e:
            logger.warning("云端模型调用失败，降级为知识检索：%s", e.message)

    # 3. 降级：知识检索
    answer = _fallback_answer(question, hits)
    return ChatResponse(
        task_id=task_id,
        answer=answer,
        source=hits[0].title if hits else "未找到匹配知识",
        references=hits,
        fallback=True,
    )


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
