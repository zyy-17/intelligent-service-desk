"""文本处理工具。"""
import re


def normalize(text: str) -> str:
    """去掉空白与常见标点，便于做字符匹配。"""
    return re.sub(r"[\s，。！？、,.!?：:；;]+", "", text or "")


def strip_markdown(text: str) -> str:
    """去掉 Markdown 标记，保留纯文本内容。"""
    return re.sub(r"#{1,6}\s*|\*|>`?|`", "", text or "").strip()


def truncate(text: str, max_len: int) -> str:
    """按字符数截断文本，超出部分用省略号代替。"""
    body = (text or "").strip()
    return body if len(body) <= max_len else body[:max_len] + "…"
