"""智能问答相关的请求与响应模型。"""
from typing import List, Optional

from pydantic import BaseModel, Field

from .common import ErrorDetail, KnowledgeItem


class UserProfile(BaseModel):
    """调用方注入的当前用户身份信息（用于个性化回答）。"""
    id: Optional[int] = None
    username: Optional[str] = None
    display_name: Optional[str] = None
    role: Optional[str] = None
    company: Optional[str] = None
    department: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    joined_at: Optional[str] = None
    ticket_total: Optional[int] = None
    ticket_processing: Optional[int] = None
    ticket_resolved: Optional[int] = None


class TicketBrief(BaseModel):
    """调用方注入的工单摘要（用户本人的近期工单）。"""
    ticket_no: str
    title: str
    status: Optional[str] = None
    priority: Optional[str] = None
    category_name: Optional[str] = None
    created_at: Optional[str] = None
    solution: Optional[str] = None


class KnowledgeBrief(BaseModel):
    """知识库目录条目（只有标题/摘要/关键词，正文由检索命中后下发）。"""
    id: int
    title: str
    summary: Optional[str] = None
    keywords: Optional[str] = None
    category_name: Optional[str] = None


class ChatRequest(BaseModel):
    """智能问答请求。"""
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    knowledge: List[KnowledgeItem] = Field(default_factory=list, description="Java 预检索的知识条目（兼容旧版）")
    knowledge_docs: List[KnowledgeItem] = Field(default_factory=list,
                                                description="已发布知识全量文档，供向量检索（含正文）")
    user_profile: Optional[UserProfile] = Field(None, description="当前用户身份信息")
    tickets: List[TicketBrief] = Field(default_factory=list, description="当前用户近期工单")
    knowledge_catalog: List[KnowledgeBrief] = Field(default_factory=list, description="知识库全量目录")
    session_id: Optional[str] = Field(None, description="会话 ID，传入则自动记录会话消息")
    user_id: Optional[int] = Field(None, description="用户 ID（会话归属）")


class KnowledgeReference(BaseModel):
    """回答依据条目：给前端展示的引用卡片（不含正文，点击后再单独拉取）。"""
    id: int
    title: str
    category_name: Optional[str] = None
    summary: Optional[str] = None
    score: Optional[float] = None
    rank: Optional[int] = None


class ChatResponse(BaseModel):
    """智能问答响应。"""
    task_id: str = Field(..., description="任务标识")
    task_type: str = "QA"
    answer: str = Field(..., description="回答内容")
    source: str = Field(..., description="答案来源（模型生成 / 知识库文章标题）")
    references: List[KnowledgeReference] = Field(default_factory=list, description="回答依据的知识文章")
    grounded: bool = Field(True, description="是否有可靠的知识依据（向量检索达到阈值）")
    fallback: bool = Field(..., description="是否降级")
    model: Optional[str] = None
    session_id: Optional[str] = Field(None, description="本轮问答归属的会话 ID")
    error: Optional[ErrorDetail] = None
