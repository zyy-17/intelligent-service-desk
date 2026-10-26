"""工单辅助能力（摘要/分类/草稿/检索）的请求与响应模型。"""
from typing import List, Optional

from pydantic import BaseModel, Field

from .common import ErrorDetail, KnowledgeItem


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


class SuggestStep(BaseModel):
    """单条处置建议步骤。"""
    order: int = Field(..., description="步骤序号，从 1 开始")
    title: str = Field(..., description="动作标题，15 字内")
    detail: str = Field("", description="具体做法，60 字内")


class SuggestStepsRequest(BaseModel):
    """工单处置建议请求。"""
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field("", max_length=5000)
    category: str = Field("", description="工单分类名称")
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="知识库参考")


class SuggestStepsResponse(BaseModel):
    """工单处置建议响应。"""
    task_id: str
    task_type: str = "SUGGEST_STEPS"
    steps: List[SuggestStep] = Field(default_factory=list)
    source: str = Field("模型生成", description="建议来源")
    fallback: bool = Field(False, description="是否降级为规则建议")
    model: Optional[str] = None
    error: Optional[ErrorDetail] = None
