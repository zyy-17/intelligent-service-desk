"""模型路由：按「云端 → Ollama」顺序构造端点链，逐个尝试，全部失败才交给上层降级。

优先级（与用户约定一致）：
1. 云端 OpenAI 兼容接口（填了 AI_API_KEY 才算可用），候选模型来自 AI_MODELS
2. 本地 Ollama（OLLAMA_ENABLED 为真且 OLLAMA_MODELS 非空），候选模型来自 OLLAMA_MODELS
3. 两者都不可用或全部失败 → 由 chat_service 用本地知识库检索作答

多模型切换就是改 .env 里 AI_MODELS / OLLAMA_MODELS 的顺序，第一个是首选，
失败自动往后换（Ollama 内部同样按顺序换），改完重启 AI 服务即可生效。
"""
import dataclasses
import logging
from typing import Callable, Iterator, List, Optional, Tuple

from ..core.config import settings
from .client import Endpoint, completion, completion_stream
from .errors import LLMError

logger = logging.getLogger(__name__)


def endpoints() -> List[Endpoint]:
    """按优先级返回可用端点列表：云端候选在前，Ollama 候选在后。"""
    chain: List[Endpoint] = []
    if settings.ai_enabled and settings.ai_api_key and settings.ai_base_url:
        for model in settings.ai_model_list:
            chain.append(Endpoint(
                kind="cloud",
                base_url=settings.ai_base_url,
                api_key=settings.ai_api_key,
                model=model,
                timeout=settings.ai_timeout_seconds,
                temperature=settings.ai_temperature,
            ))
    if settings.ollama_enabled:
        for model in settings.ollama_model_list:
            chain.append(Endpoint(
                kind="ollama",
                base_url=settings.ollama_base_url,
                api_key=settings.ollama_api_key,
                model=model,
                timeout=settings.ollama_timeout_seconds,
                temperature=settings.ollama_temperature,
            ))
    return chain


def has_endpoint() -> bool:
    """是否存在任何可用的模型端点。"""
    return bool(endpoints())


def _with_temperature(ep: Endpoint, temperature: Optional[float]) -> Endpoint:
    """按调用方要求覆盖温度，其余连接信息不变。"""
    if temperature is None:
        return ep
    return dataclasses.replace(ep, temperature=temperature)


def generate(messages: List[dict], temperature: Optional[float] = None) -> Tuple[str, Endpoint]:
    """按优先级生成回答，返回 (文本, 实际使用的端点)；全部失败抛 LLMError。"""
    chain = endpoints()
    if not chain:
        raise LLMError("AI_NOT_CONFIGURED", "未配置任何模型（云端未填密钥且 Ollama 未启用）")

    errors: List[str] = []
    for ep in chain:
        actual = _with_temperature(ep, temperature)
        try:
            text = completion(actual, messages)
            if errors:
                logger.warning("已降级到 %s（前序失败：%s）", actual.label, "；".join(errors))
            else:
                logger.info("模型调用成功：%s", actual.label)
            return text, actual
        except LLMError as e:
            errors.append(e.message)
            logger.warning("模型 %s 调用失败，尝试下一个：%s", actual.label, e.message)

    raise LLMError("AI_ALL_FAILED", "；".join(errors))


def stream(messages: List[dict], temperature: Optional[float] = None,
           on_used: Optional[Callable[[Endpoint], None]] = None) -> Iterator[str]:
    """按优先级流式生成，逐段产出文本。

    on_used 在首个字节产出时回调，告知实际使用的端点；
    一旦已经输出过内容就不再切换（否则前端会把两个模型的回答拼在一起）。
    """
    chain = endpoints()
    if not chain:
        raise LLMError("AI_NOT_CONFIGURED", "未配置任何模型（云端未填密钥且 Ollama 未启用）")

    errors: List[str] = []
    for ep in chain:
        actual = _with_temperature(ep, temperature)
        produced = False
        try:
            for piece in completion_stream(actual, messages):
                if not produced:
                    produced = True
                    if on_used is not None:
                        on_used(actual)
                yield piece
            if not produced:
                raise LLMError("AI_EMPTY_RESULT", f"{actual.label} 返回空内容")
            if errors:
                logger.warning("已降级到 %s（前序失败：%s）", actual.label, "；".join(errors))
            else:
                logger.info("流式调用成功：%s", actual.label)
            return
        except LLMError as e:
            if produced:
                # 已经吐出一部分，换模型会拼出两段回答，交给上层用知识库兜底
                raise LLMError("AI_STREAM_INTERRUPTED", f"{actual.label} 输出中断：{e.message}")
            errors.append(e.message)
            logger.warning("模型 %s 流式调用失败，尝试下一个：%s", actual.label, e.message)

    raise LLMError("AI_ALL_FAILED", "；".join(errors))
