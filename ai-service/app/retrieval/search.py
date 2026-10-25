"""知识检索：本地字符 bigram 相关性排序，不依赖任何外部服务。"""
from collections import Counter
from typing import List

from ..schemas import KnowledgeItem
from ..utils.text import normalize, strip_markdown


def search_knowledge(query: str, knowledge: List[KnowledgeItem], top_k: int = 5) -> List[KnowledgeItem]:
    """在候选知识中做 2 字短语命中检索，按命中数降序返回前 top_k 条。"""
    if not knowledge:
        return []

    q = normalize(query)
    if len(q) < 2:
        return []

    grams = {q[i:i + 2] for i in range(len(q) - 1)}
    scores: Counter = Counter()
    for item in knowledge:
        haystack = normalize(item.title) + normalize(item.keywords or "")
        hits = sum(1 for g in grams if g in haystack)
        if hits > 0:
            scores[item.id] = hits

    # 至少命中 2 个短语才认为相关，避免噪音
    ranked = [k for k, v in scores.items() if v >= 2]
    ranked.sort(key=lambda k: scores[k], reverse=True)

    by_id = {item.id: item for item in knowledge}
    return [by_id[kid] for kid in ranked[:top_k] if kid in by_id]


def build_context(knowledge: List[KnowledgeItem], max_body_len: int = 400) -> str:
    """把命中的知识条目组装成受长度约束的上下文文本。"""
    parts = []
    for item in knowledge:
        body = strip_markdown(item.body or "")
        if len(body) > max_body_len:
            body = body[:max_body_len]
        parts.append(f"【{item.title}】{body}")
    return "\n".join(parts)
