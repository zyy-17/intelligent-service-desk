"""智能问答相关的请求与响应模型。"""
from typing import List, Optional

from pydantic import BaseModel, Field

from .common import ErrorDetail, KnowledgeItem


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
