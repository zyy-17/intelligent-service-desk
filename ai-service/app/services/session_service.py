"""会话业务：多轮上下文读取与一轮问答的落库。"""
import logging
from typing import Dict, List, Optional

from ..core.config import settings
from ..schemas import ChatRequest
from ..storage import store

logger = logging.getLogger("ai-service.sessions")


def recent_history(session_id: Optional[str], user_id: Optional[int], limit: Optional[int] = None) -> List[Dict[str, str]]:
    """取会话最近若干条消息作为多轮上下文，异常时返回空列表。"""
    if not session_id or not user_id:
        return []
    try:
        data = store.get(session_id, user_id)
        if not data:
            return []
        window = limit or settings.history_window
        return [
            {"role": m["role"], "content": m["content"]}
            for m in data["messages"][-window:]
            if m.get("role") in ("user", "assistant") and m.get("content")
        ]
    except Exception as e:
        logger.warning("读取会话历史失败：%s", e)
        return []


def record_turn(req: ChatRequest, question: str, answer: str,
                references: Optional[List[dict]] = None,
                grounded: Optional[bool] = None,
                source: Optional[str] = None,
                model: Optional[str] = None) -> Optional[str]:
    """把一轮问答写入会话（会话不存在时自动新建），附带引用信息供历史回显，返回会话 ID。"""
    if not req.user_id:
        return None
    try:
        # 引用随助手消息一起落库，重新打开会话时前端才能还原「回答依据」卡片
        extra = {"references": references, "grounded": grounded, "source": source, "model": model}
        sid = req.session_id
        appended = False
        if sid:
            appended = store.append_message(sid, req.user_id, "user", question)
            if not appended:
                # 会话已失效（例如被删除），走新建流程
                sid = None
        if not sid:
            sid = store.create(req.user_id)["session_id"]
            appended = store.append_message(sid, req.user_id, "user", question)
        if not appended:
            logger.warning("会话 %s 写入用户消息失败", sid)
        store.rename_if_first(sid, req.user_id)
        store.append_message(sid, req.user_id, "assistant", answer, extra)
        return sid
    except Exception as e:
        logger.warning("会话写入失败：%s", e)
        return None
