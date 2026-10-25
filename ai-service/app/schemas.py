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
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="Java 传入的知识库上下文")


class ChatResponse(BaseModel):
    task_id: str = Field(..., description="任务标识")
    task_type: str = "QA"
    answer: str = Field(..., description="回答内容")
    source: str = Field(..., description="答案来源（模型生成 / 知识库文章标题）")
    references: List[KnowledgeItem] = Field(default_factory=list, description="引用的知识")
    fallback: bool = Field(..., description="是否降级")
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 工单摘要 =====

class SummaryRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)


class SummaryResponse(BaseModel):
    task_id: str
    task_type: str = "SUMMARY"
    summary: str
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 分类推荐 =====

class CategoryOption(BaseModel):
    id: int
    name: str


class ClassifyRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)
    categories: List[CategoryOption] = Field(..., description="候选分类列表")


class ClassifyResponse(BaseModel):
    task_id: str
    task_type: str = "CLASSIFY"
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    confidence: float = Field(..., ge=0, le=1)
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 回复草稿 =====

class DraftRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=5000)
    knowledge: List[KnowledgeItem] = Field(default_factory=list)


class DraftResponse(BaseModel):
    task_id: str
    task_type: str = "DRAFT"
    draft: str
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None


# ===== 知识检索 =====

class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=500)
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="候选知识列表")


class SearchResponse(BaseModel):
    task_id: str
    task_type: str = "SEARCH"
    results: List[KnowledgeItem]
    error: Optional[ErrorDetail] = None
