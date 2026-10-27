"""知识检索：向量检索（TF-IDF + 余弦相似度）、上下文构建与引用整理。

检索只在 Java 下发的「已发布知识文章」范围内进行，模型回答必须以命中内容为依据。
"""
from typing import List

from ..core.config import settings
from ..schemas import KnowledgeItem
from ..utils.text import strip_markdown
from .vector import vector_search


def search_knowledge(query: str, knowledge: List[KnowledgeItem], top_k: int = 0) -> List[KnowledgeItem]:
    """向量检索：返回余弦相似度达到阈值的知识条目，score 写入每条结果。"""
    threshold = settings.retrieval_min_score
    limit = top_k or settings.knowledge_top_k
    hits = vector_search(query, knowledge, top_k=limit, min_score=threshold)
    return [
        item.model_copy(update={"score": round(score, 4)})
        for item, score in hits
    ]


def build_context(knowledge: List[KnowledgeItem], max_body_len: int = 0) -> str:
    """把命中的知识条目组装成带编号与相关度的上下文文本，便于模型引用。"""
    if not knowledge:
        return ""
    limit = max_body_len or settings.knowledge_context_chars
    parts = []
    for idx, item in enumerate(knowledge, 1):
        body = strip_markdown(item.body or "")
        if len(body) > limit:
            body = body[:limit] + "…"
        score = f"｜相关度 {item.score:.0%}" if item.score is not None else ""
        parts.append(f"〔资料{idx}〕《{item.title}》{score}\n{body}")
    return "\n\n".join(parts)


def to_references(knowledge: List[KnowledgeItem]) -> List[dict]:
    """整理给前端展示的引用列表（不含正文，避免响应体过大）。"""
    references = []
    for idx, item in enumerate(knowledge, 1):
        references.append({
            "id": item.id,
            "title": item.title,
            "category_name": item.category_name,
            "summary": (item.summary or "")[:120],
            "score": item.score,
            "rank": idx,
        })
    return references