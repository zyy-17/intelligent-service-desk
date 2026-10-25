"""会话管理相关的请求与响应模型。"""
from typing import List, Optional

from pydantic import BaseModel, Field


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
