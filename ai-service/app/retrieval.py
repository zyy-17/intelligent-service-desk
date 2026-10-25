"""知识检索与提示词构建。"""
import re
from collections import Counter
from typing import List

from .schemas import KnowledgeItem


def _normalize(text: str) -> str:
    """去掉空白与常见标点，便于文本匹配。"""
    return re.sub(r"[\s，。！？、,.!?：:；;]+", "", text or "")


def search_knowledge(query: str, knowledge: List[KnowledgeItem], top_k: int = 5) -> List[KnowledgeItem]:
    """在候选知识中做字符 bigram 相关性检索，按命中次数排序。"""
    if not knowledge:
        return []

    q = _normalize(query)
    if not q:
        return []

    # 生成 2 字短语
    grams = {q[i:i + 2] for i in range(len(q) - 1)}
    if not grams:
        return []

    scores: Counter = Counter()
    for item in knowledge:
        text = _normalize(item.title) + _normalize(item.keywords or "")
        hits = sum(1 for g in grams if g in text)
        if hits > 0:
            scores[item.id] = hits

    # 至少命中 2 个短语才认为相关
    ranked = [k for k, v in scores.items() if v >= 2]
    ranked.sort(key=lambda k: scores[k], reverse=True)

    result = []
    by_id = {item.id: item for item in knowledge}
    for kid in ranked[:top_k]:
        if kid in by_id:
            result.append(by_id[kid])
    return result


def build_context(knowledge: List[KnowledgeItem], max_body_len: int = 400) -> str:
    """把知识条目组装成受控上下文。"""
    parts = []
    for item in knowledge:
        body = re.sub(r"#{1,6}\s*|\*|>`?|`", "", item.body or "").strip()
        if len(body) > max_body_len:
            body = body[:max_body_len]
        parts.append(f"【{item.title}】{body}")
    return "\n".join(parts)


SYSTEM_PROMPT = (
    "你是「智能客服与工单管理平台」的客服助手，请用简洁、专业的中文回答用户问题。"
    "优先依据下面提供的知识库内容回答；如果知识库内容不足，可以结合常识回答，"
    "但不要编造具体数字或承诺。"
)
