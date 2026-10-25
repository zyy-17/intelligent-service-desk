"""工单辅助业务：摘要、分类推荐、回复草稿、纯本地知识检索。"""
import logging
import re
from typing import List, Optional

from ..core.config import settings
from ..llm import LLMError, chat_completion, is_configured
from ..prompts import build_classify_messages, build_draft_messages, build_summary_messages
from ..retrieval import build_context, search_knowledge
from ..schemas import (
    CategoryOption,
    ClassifyRequest,
    ClassifyResponse,
    DraftRequest,
    DraftResponse,
    ErrorDetail,
    SearchRequest,
    SearchResponse,
    SummaryRequest,
    SummaryResponse,
)
from .chat_service import new_task_id

logger = logging.getLogger("ai-service.ticket")

# 关键词兜底映射：模型不可用时按分类名匹配业务关键词
KEYWORD_MAP = {
    "账户": ["账户", "登录", "密码", "账号", "验证"],
    "产品咨询": ["产品", "功能", "咨询", "介绍", "套餐"],
    "技术故障": ["故障", "报错", "闪退", "卡", "超时", "乱码", "异常"],
    "账单与支付": ["退款", "账单", "发票", "支付", "续费", "金额"],
    "意见建议": ["建议", "希望", "增加", "意见"],
}

DEFAULT_REPLY = "您好，我们已收到您的工单，正在为您处理，请稍候。"


def summarize(req: SummaryRequest) -> SummaryResponse:
    """生成一句话工单摘要，模型不可用时退化为原文截断。"""
    task_id = new_task_id()
    if not is_configured():
        return SummaryResponse(
            task_id=task_id,
            summary=f"{req.title}：{req.description[:100]}",
            error=ErrorDetail(code="AI_NOT_CONFIGURED", message="未配置云端模型"),
        )
    try:
        summary = chat_completion(build_summary_messages(req.title, req.description), temperature=0.2)
        return SummaryResponse(task_id=task_id, summary=summary, model=settings.ai_model)
    except LLMError as e:
        return SummaryResponse(
            task_id=task_id,
            summary=f"{req.title}：{req.description[:100]}",
            error=ErrorDetail(code=e.code, message=e.message),
        )


def classify(req: ClassifyRequest) -> ClassifyResponse:
    """推荐工单分类，模型不可用或解析失败时用关键词兜底。"""
    task_id = new_task_id()
    if not req.categories:
        return ClassifyResponse(task_id=task_id, confidence=0.0)

    text = f"{req.title}{req.description}"
    if not is_configured():
        return _keyword_result(task_id, text, req.categories)

    try:
        raw = chat_completion(
            build_classify_messages(req.title, req.description, [(c.id, c.name) for c in req.categories]),
            temperature=0.0,
        )
        cid = _extract_int(raw)
        if cid is None:
            raise LLMError("AI_PARSE_ERROR", "分类结果无有效 ID")
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
        return _keyword_result(task_id, text, req.categories, error=e)


def draft(req: DraftRequest) -> DraftResponse:
    """根据工单与知识库生成客服回复草稿。"""
    task_id = new_task_id()
    if not is_configured():
        return DraftResponse(
            task_id=task_id,
            draft=DEFAULT_REPLY,
            error=ErrorDetail(code="AI_NOT_CONFIGURED", message="未配置云端模型"),
        )
    try:
        context = build_context(req.knowledge)
        text = chat_completion(build_draft_messages(req.title, req.description, context), temperature=0.5)
        return DraftResponse(task_id=task_id, draft=text, model=settings.ai_model)
    except LLMError as e:
        return DraftResponse(
            task_id=task_id,
            draft=DEFAULT_REPLY,
            error=ErrorDetail(code=e.code, message=e.message),
        )


def search(req: SearchRequest) -> SearchResponse:
    """纯本地知识检索，不依赖模型。"""
    return SearchResponse(
        task_id=new_task_id(),
        results=search_knowledge(req.query, req.knowledge, settings.knowledge_top_k),
    )


def _keyword_result(
    task_id: str,
    text: str,
    categories: List[CategoryOption],
    error: Optional[LLMError] = None,
) -> ClassifyResponse:
    """关键词兜底分类，可选携带模型错误信息。"""
    detail = ErrorDetail(code=error.code, message=error.message) if error else None
    matched = _keyword_classify(text, categories)
    if matched is None:
        return ClassifyResponse(task_id=task_id, confidence=0.0, error=detail)
    return ClassifyResponse(
        task_id=task_id,
        category_id=matched.id,
        category_name=matched.name,
        confidence=0.6,
        error=detail,
    )


def _keyword_classify(text: str, categories: List[CategoryOption]) -> Optional[CategoryOption]:
    """按分类名对应的业务关键词做兜底匹配。"""
    for category in categories:
        for keyword in KEYWORD_MAP.get(category.name, []):
            if keyword in text:
                return category
    return None


def _extract_int(raw: str) -> Optional[int]:
    """从模型输出中提取第一个整数。"""
    match = re.search(r"\d+", raw or "")
    return int(match.group()) if match else None
