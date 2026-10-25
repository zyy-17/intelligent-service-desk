"""通用数据模型：知识条目与错误详情。"""
from typing import Optional

from pydantic import BaseModel


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
