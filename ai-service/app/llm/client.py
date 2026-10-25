"""云端大模型调用封装（OpenAI 兼容的 /chat/completions 接口）。

只负责「发请求、收结果、统一错误码」，不掺杂任何业务语义；
提示词组装在 prompts 层，业务编排在 services 层。
"""
import json
import logging
from typing import Any, Dict, Iterator, List

import httpx

from ..core.config import settings
from .errors import LLMError

logger = logging.getLogger(__name__)


def is_configured() -> bool:
    """判断云端模型是否已配置（开关打开且填了密钥与地址）。"""
    return bool(settings.ai_enabled and settings.ai_api_key and settings.ai_base_url)


def _endpoint() -> str:
    """拼接 chat/completions 完整地址。"""
    return settings.ai_base_url.rstrip("/") + "/chat/completions"


def _headers() -> Dict[str, str]:
    """构造鉴权请求头。"""
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {settings.ai_api_key}",
    }


def _payload(messages: List[Dict[str, str]], temperature: float | None, stream: bool = False) -> Dict[str, Any]:
    """构造请求体。"""
    body: Dict[str, Any] = {
        "model": settings.ai_model,
        "temperature": settings.ai_temperature if temperature is None else temperature,
        "messages": messages,
    }
    if stream:
        body["stream"] = True
    return body


def chat_completion(messages: List[Dict[str, str]], temperature: float | None = None) -> str:
    """一次性调用模型，返回完整回答文本。"""
    if not is_configured():
        raise LLMError("AI_NOT_CONFIGURED", "未配置云端模型密钥")

    try:
        with httpx.Client(timeout=settings.ai_timeout_seconds) as client:
            resp = client.post(_endpoint(), json=_payload(messages, temperature), headers=_headers())
    except httpx.TimeoutException:
        raise LLMError("AI_TIMEOUT", f"模型调用超时（>{settings.ai_timeout_seconds}s）")
    except httpx.RequestError as e:
        raise LLMError("AI_NETWORK_ERROR", f"模型服务不可达：{e}")

    if resp.status_code != 200:
        raise LLMError("AI_HTTP_ERROR", f"模型接口返回 {resp.status_code}")

    try:
        data = resp.json()
        choices = data.get("choices") or []
        if not choices:
            raise LLMError("AI_EMPTY_RESULT", "模型返回无结果")
        content = choices[0].get("message", {}).get("content", "")
        if not content or not content.strip():
            raise LLMError("AI_EMPTY_RESULT", "模型返回空内容")
        return content.strip()
    except LLMError:
        raise
    except Exception as e:
        raise LLMError("AI_PARSE_ERROR", f"模型响应解析失败：{e}")


def chat_completion_stream(messages: List[Dict[str, str]], temperature: float | None = None) -> Iterator[str]:
    """流式调用模型，逐段产出增量文本（SSE 的 data: 行）。"""
    if not is_configured():
        raise LLMError("AI_NOT_CONFIGURED", "未配置云端模型密钥")

    try:
        with httpx.Client(timeout=settings.ai_timeout_seconds) as client:
            with client.stream(
                "POST",
                _endpoint(),
                json=_payload(messages, temperature, stream=True),
                headers=_headers(),
            ) as resp:
                if resp.status_code != 200:
                    raise LLMError("AI_HTTP_ERROR", f"模型接口返回 {resp.status_code}")
                for raw_line in resp.iter_lines():
                    piece = _parse_sse_line(raw_line)
                    if piece:
                        yield piece
    except LLMError:
        raise
    except httpx.TimeoutException:
        raise LLMError("AI_TIMEOUT", f"模型调用超时（>{settings.ai_timeout_seconds}s）")
    except httpx.RequestError as e:
        raise LLMError("AI_NETWORK_ERROR", f"模型服务不可达：{e}")
    except Exception as e:
        raise LLMError("AI_STREAM_ERROR", f"流式响应解析失败：{e}")


def _parse_sse_line(raw_line: str) -> str:
    """从单行 SSE 数据中取出 delta 文本，非数据行返回空串。"""
    line = (raw_line or "").strip()
    if not line or not line.startswith("data:"):
        return ""
    data = line[5:].strip()
    if not data or data == "[DONE]":
        return ""
    try:
        chunk = json.loads(data)
        choices = chunk.get("choices") or [{}]
        return choices[0].get("delta", {}).get("content", "") or ""
    except (json.JSONDecodeError, AttributeError, IndexError):
        return ""
