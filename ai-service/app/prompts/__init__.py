"""提示词层：按场景拆分系统角色提示词，统一从此处导出。

每个场景一个文件，文件内提供 build_messages() 组装该场景的完整消息列表。
"""
from .chat import build_messages as build_chat_messages
from .chat import system_prompt as chat_system_prompt
from .classify import build_messages as build_classify_messages
from .draft import build_messages as build_draft_messages
from .summary import build_messages as build_summary_messages

__all__ = [
    "build_chat_messages",
    "chat_system_prompt",
    "build_summary_messages",
    "build_classify_messages",
    "build_draft_messages",
]
