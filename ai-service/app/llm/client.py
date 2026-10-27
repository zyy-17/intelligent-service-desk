"""模型调用层：单端点 HTTP 封装（OpenAI 兼容 /chat/completions）。

只负责「按端点发请求、收结果、统一错误码」，不做优先级选择（那在 router 层）；
提示词组装在 prompts 层，业务编排在 services 层。
云端与本地 Ollama 共用同一套代码，区别只在 base_url / api_key / model。
"""
import json
import logging
from dataclasses import dataclass
from typing import Any, Dict, Iterator, List

import httpx

from .errors import LLMError

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Endpoint:
    """一个可调用的模型端点（云端或本地 Ollama），只描述连接信息。"""
    kind: str          # cloud / ollama
    base_url: str
    api_key: str
    model: str
    timeout: float
    temperature: float

    @property
    def label(self) -> str:
        """展示名：写进 done 事件、日志与会话记录，如「deepseek-chat · 云端」。"""
        return f"{self.model} · {'Ollama' if self.kind == 'ollama' else '云端'}"


def _endpoint_url(ep: Endpoint) -> str:
    """拼接 chat/completions 完整地址。"""
    return ep.base_url.rstrip("/") + "/chat/completions"


def _headers(ep: Endpoint) -> Dict[str, str]:
    """构造鉴权请求头（Ollama 不校验 key，带上也无妨）。"""
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ep.api_key}",
    }


def _payload(ep: Endpoint, messages: List[Dict[str, str]], stream: bool = False) -> Dict[str, Any]:
    """构造请求体。"""
    body: Dict[str, Any] = {
        "model": ep.model,
        "temperature": ep.temperature,
        "messages": messages,
    }
    if stream:
        body["stream"] = True
    return body


def completion(ep: Endpoint, messages: List[Dict[str, str]]) -> str:
    """调用指定端点，返回完整回答文本。"""
    try:
        with httpx.Client(timeout=ep.timeout) as client:
            resp = client.post(_endpoint_url(ep), json=_payload(ep, messages),
                               headers=_headers(ep))
    except httpx.TimeoutException:
        raise LLMError("AI_TIMEOUT", f"{ep.label} 调用超时（>{ep.timeout:g}s）")
    except httpx.RequestError as e:
        raise LLMError("AI_NETWORK_ERROR", f"{ep.label} 服务不可达：{e}")

    if resp.status_code != 200:
        raise LLMError("AI_HTTP_ERROR", f"{ep.label} 返回 {resp.status_code}")

    try:
        data = resp.json()
        choices = data.get("choices") or []
        if not choices:
            raise LLMError("AI_EMPTY_RESULT", f"{ep.label} 返回无结果")
        content = choices[0].get("message", {}).get("content", "")
        if not content or not content.strip():
            raise LLMError("AI_EMPTY_RESULT", f"{ep.label} 返回空内容")
        return content.strip()
    except LLMError:
        raise
    except Exception as e:
        raise LLMError("AI_PARSE_ERROR", f"{ep.label} 响应解析失败：{e}")


def completion_stream(ep: Endpoint, messages: List[Dict[str, str]]) -> Iterator[str]:
    """流式调用指定端点，逐段产出增量文本（SSE 的 data: 行）。"""
    try:
        with httpx.Client(timeout=ep.timeout) as client:
            with client.stream(
                "POST",
                _endpoint_url(ep),
                json=_payload(ep, messages, stream=True),
                headers=_headers(ep),
            ) as resp:
                if resp.status_code != 200:
                    raise LLMError("AI_HTTP_ERROR", f"{ep.label} 返回 {resp.status_code}")
                for raw_line in resp.iter_lines():
                    piece = parse_sse_line(raw_line)
                    if piece:
                        yield piece
    except LLMError:
        raise
    except httpx.TimeoutException:
        raise LLMError("AI_TIMEOUT", f"{ep.label} 调用超时（>{ep.timeout:g}s）")
    except httpx.RequestError as e:
        raise LLMError("AI_NETWORK_ERROR", f"{ep.label} 服务不可达：{e}")
    except Exception as e:
        raise LLMError("AI_STREAM_ERROR", f"{ep.label} 流式响应解析失败：{e}")


def parse_sse_line(raw_line: str) -> str:
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
