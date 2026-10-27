"""模型调用层：端点 HTTP 封装 + 云端→Ollama 的多模型路由。

- client.py：单端点调用（OpenAI 兼容 /chat/completions）
- router.py：按 .env 里的候选顺序尝试，失败自动换下一个
- 本模块额外提供不带端点信息的兼容函数，供摘要/分类/草稿等场景直接调用
"""
from typing import Iterator, List, Optional

from .client import Endpoint, completion, completion_stream
from .errors import LLMError
from .router import endpoints, generate, has_endpoint, stream

__all__ = [
    "Endpoint",
    "LLMError",
    "endpoints",
    "generate",
    "has_endpoint",
    "stream",
    "chat_completion",
    "chat_completion_stream",
    "is_configured",
]


def chat_completion(messages: List[dict], temperature: Optional[float] = None) -> str:
    """兼容旧调用：只要文本，不关心实际用了哪个端点。"""
    return generate(messages, temperature)[0]


def chat_completion_stream(messages: List[dict], temperature: Optional[float] = None) -> Iterator[str]:
    """兼容旧调用：只要流式文本，不关心实际用了哪个端点。"""
    yield from stream(messages, temperature)


def is_configured() -> bool:
    """是否至少有一个可用模型端点（云端或 Ollama）。"""
    return has_endpoint()
