"""会话管理路由：创建、列表、详情、删除。"""
from typing import List

from fastapi import APIRouter, HTTPException

from ..schemas import SessionCreateRequest, SessionDetail, SessionSummary
from ..storage import store

router = APIRouter(prefix="/api/ai/sessions", tags=["会话管理"])


@router.post("")
def create_session(req: SessionCreateRequest):
    """创建新会话。"""
    return store.create(req.user_id, req.title)


@router.get("", response_model=List[SessionSummary])
def list_sessions(user_id: int, limit: int = 50):
    """查询某用户的会话列表。"""
    return store.list(user_id, limit)


@router.get("/{session_id}", response_model=SessionDetail)
def get_session(session_id: str, user_id: int):
    """查询会话详情（含历史消息）。"""
    data = store.get(session_id, user_id)
    if data is None:
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")
    return data


@router.delete("/{session_id}")
def delete_session(session_id: str, user_id: int):
    """删除会话。"""
    if not store.delete(session_id, user_id):
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")
    return {"deleted": True}
