"""云端大模型调用封装（OpenAI 兼容接口）。"""
import logging
from typing import List, Dict, Any, Iterator

import httpx

from .config import settings

logger = logging.getLogger(__name__)


class LLMError(Exception):
    """模型调用错误。"""
    def __init__(self, code: str, message: str):
        self.code = code
        self.message = message
        super().__init__(message)


def is_configured() -> bool:
    """判断云端模型是否已配置（启用且填了密钥与地址）。"""
    return (
        settings.ai_enabled
        and bool(settings.ai_api_key)
        and bool(settings.ai_base_url)
    )


def chat_completion(
    messages: List[Dict[str, str]],
    temperature: float | None = None,
) -> str:
    """调用 OpenAI 兼容的 chat/completions 接口，返回模型文本。"""
    if not is_configured():
        raise LLMError("AI_NOT_CONFIGURED", "未配置云端模型密钥")

    url = settings.ai_base_url.rstrip("/") + "/chat/completions"
    payload: Dict[str, Any] = {
        "model": settings.ai_model,
        "temperature": settings.ai_temperature if temperature is None else temperature,
        "messages": messages,
    }

    try:
        with httpx.Client(timeout=settings.ai_timeout_seconds) as client:
            resp = client.post(
                url,
                json=payload,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {settings.ai_api_key}",
                },
            )
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


def chat_completion_stream(
    messages: List[Dict[str, str]],
    temperature: float | None = None,
) -> Iterator[str]:
    """流式调用 OpenAI 兼容的 chat/completions 接口，逐段产出增量文本。"""
    if not is_configured():
        raise LLMError("AI_NOT_CONFIGURED", "未配置云端模型密钥")

    url = settings.ai_base_url.rstrip("/") + "/chat/completions"
    payload: Dict[str, Any] = {
        "model": settings.ai_model,
        "temperature": settings.ai_temperature if temperature is None else temperature,
        "messages": messages,
        "stream": True,
    }

    try:
        with httpx.Client(timeout=settings.ai_timeout_seconds) as client:
            with client.stream(
                "POST",
                url,
                json=payload,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {settings.ai_api_key}",
                },
            ) as resp:
                if resp.status_code != 200:
                    raise LLMError("AI_HTTP_ERROR", f"模型接口返回 {resp.status_code}")
                for line in resp.iter_lines():
                    if not line:
                        continue
                    line = line.strip()
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        import json as _json
                        chunk = _json.loads(data)
                        delta = (chunk.get("choices") or [{}])[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except Exception:
                        continue
    except LLMError:
        raise
    except httpx.TimeoutException:
        raise LLMError("AI_TIMEOUT", f"模型调用超时（>{settings.ai_timeout_seconds}s）")
    except httpx.RequestError as e:
        raise LLMError("AI_NETWORK_ERROR", f"模型服务不可达：{e}")
    except Exception as e:
        raise LLMError("AI_STREAM_ERROR", f"流式响应解析失败：{e}")

