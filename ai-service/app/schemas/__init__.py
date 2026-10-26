"""数据模型层：按业务场景拆分的 Pydantic 模型，统一从此处导出。"""
from .chat import ChatRequest, ChatResponse, KnowledgeBrief, TicketBrief, UserProfile
from .common import ErrorDetail, KnowledgeItem
from .session import ChatMessage, SessionCreateRequest, SessionDetail, SessionSummary
from .ticket import (
    CategoryOption,
    ClassifyRequest,
    ClassifyResponse,
    DraftRequest,
    DraftResponse,
    SearchRequest,
    SearchResponse,
    SuggestStep,
    SuggestStepsRequest,
    SuggestStepsResponse,
    SummaryRequest,
    SummaryResponse,
)

__all__ = [
    "KnowledgeItem",
    "ErrorDetail",
    "ChatRequest",
    "ChatResponse",
    "UserProfile",
    "TicketBrief",
    "KnowledgeBrief",
    "SummaryRequest",
    "SummaryResponse",
    "CategoryOption",
    "ClassifyRequest",
    "ClassifyResponse",
    "DraftRequest",
    "DraftResponse",
    "SearchRequest",
    "SearchResponse",
    "SuggestStep",
    "SuggestStepsRequest",
    "SuggestStepsResponse",
    "SessionCreateRequest",
    "ChatMessage",
    "SessionDetail",
    "SessionSummary",
]
