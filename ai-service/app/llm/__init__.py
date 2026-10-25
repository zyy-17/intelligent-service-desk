"""模型调用层：OpenAI 兼容接口的请求与错误封装。"""
from .client import chat_completion, chat_completion_stream, is_configured
from .errors import LLMError

__all__ = ["chat_completion", "chat_completion_stream", "is_configured", "LLMError"]
