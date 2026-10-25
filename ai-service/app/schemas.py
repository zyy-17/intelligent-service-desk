"""Pydantic 请求与响应模型。"""
from typing import List, Optional

from pydantic import BaseModel, Field


# ===== 通用 =====

class KnowledgeItem(BaseModel):
    """知识库检索返回的知识条目。"""
    id: int
    title: str
    summary: Optional[str] = None
    body: Optional[str] = None
    keywords: Optional[str] = None


class ErrorDetail(BaseModel):
    """可追踪的错误信息。"""
    code: str
    message: str


# ===== 智能问答 =====

class ChatRequest(BaseModel):
    """智能问答请求。"""
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="Java 传入的知识库上下文")
    session_id: Optional[str] = Field(None, description="会话 ID，传入则自动记录会话消息")
    user_id: Optional[int] = Field(None, description="用户 ID（会话归属）")


class ChatResponse(BaseModel):
    """智能问答响应。"""
    task_id: str = Field(..., description="任务标识")
    task_type: str = "QA"
    answer: str = Field(..., description="回答内容")
    source: str = Field(..., description="答案来源（模型生成 / 知识库文章标题）")
    references: List[KnowledgeItem] = Field(default_factory=list, description="引用的知识")
    fallback: bool = Field(..., description="是否降级")
    model: Optional[str] = None
    session_id: Optional[str] = Field(None, description="本轮问答归属的会话 ID")
    error: Optional[ErrorDetail] = None


# ===== 工单摘要 =====

class SummaryRequest(BaseModel):
    """工单摘要请求。"""
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)


class SummaryResponse(BaseModel):
    """工单摘要响应。"""
    task_id: str
    task_type: str = "SUMMARY"
    summary: str
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 分类推荐 =====

class CategoryOption(BaseModel):
    """候选分类项。"""
    id: int
    name: str


class ClassifyRequest(BaseModel):
    """分类推荐请求。"""
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)
    categories: List[CategoryOption] = Field(..., description="候选分类列表")


class ClassifyResponse(BaseModel):
    """分类推荐响应。"""
    task_id: str
    task_type: str = "CLASSIFY"
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    confidence: float = Field(..., ge=0, le=1)
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 回复草稿 =====

class DraftRequest(BaseModel):
    """回复草稿请求。"""
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)
    knowledge: List[KnowledgeItem] = Field(default_factory=list)


class DraftResponse(BaseModel):
    """回复草稿响应。"""
    task_id: str
    task_type: str = "DRAFT"
    draft: str
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 知识检索 =====

class SearchRequest(BaseModel):
    """知识检索请求。"""
    query: str = Field(..., min_length=1, max_length=500)
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="候选知识列表")


class SearchResponse(BaseModel):
    """知识检索响应。"""
    task_id: str
    task_type: str = "SEARCH"
    results: List[KnowledgeItem]
    error: Optional[ErrorDetail] = None


# ===== 会话管理 =====

class SessionCreateRequest(BaseModel):
    """会话创建请求。"""
    user_id: int = Field(..., description="用户 ID")
    title: str = Field("新会话", max_length=100, description="会话标题")


class ChatMessage(BaseModel):
    """会话消息。"""
    role: str
    content: str
    ts: Optional[float] = None


class SessionDetail(BaseModel):
    """会话详情（含消息列表）。"""
    session_id: str
    title: str
    created_at: float
    messages: List[ChatMessage]


class SessionSummary(BaseModel):
    """会话列表项。"""
    session_id: str
    title: str
    preview: str = ""
    created_at: float
    updated_at: float
