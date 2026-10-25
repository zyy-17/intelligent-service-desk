"""SSE（Server-Sent Events）编码工具。"""
import json


def encode(event: str, data: str = "", **meta) -> str:
    """构造单条 SSE 消息：事件名 + 增量文本 + 附加元信息。"""
    payload = {"event": event, "data": data}
    if meta:
        payload.update(meta)
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
