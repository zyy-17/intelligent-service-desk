"""向量检索：把知识文章与用户问题都映射成 TF-IDF 稀疏向量，用余弦相似度排序。

纯标准库实现，不引入 numpy / 向量数据库，离线可用：
- 切词：中文按 2 字短语（bigram），英文与数字按单词
- 字段加权：标题 > 关键词 > 摘要 > 正文
- 词频做次线性压缩（1 + log tf），乘 IDF 后 L2 归一化
- 余弦相似度 = 两个归一化向量的点积
- 语料指纹做缓存键，知识库没变化时索引只构建一次
"""
from __future__ import annotations

import hashlib
import math
import re
import threading
from collections import Counter, OrderedDict
from typing import Dict, List, Optional, Sequence, Tuple

from ..schemas.common import KnowledgeItem
from ..utils.text import normalize

# 中文里几乎不含区分度的高频 2 字组合（纯功能词，不做业务词过滤，避免误伤「问题」「账户」等词）
STOP_GRAMS = frozenset({
    "的了", "我们", "你们", "他们", "这个", "那个", "就是", "如果", "因为", "所以",
    "但是", "然后", "还是", "可以", "什么", "怎么", "已经", "以及", "或者", "而且",
    "并且", "不是", "不能", "可能", "应该", "一下", "这样", "那样", "时候", "现在",
    "进行", "通过", "一个", "一些", "有没", "没有", "的话", "之后", "之前", "同时",
})

# 提问里的泛化说法：出现频率极高且几乎不含业务信息，不参与「核心词」判定
GENERIC_GRAMS = frozenset({
    "如何", "怎么", "么办", "怎样", "为什", "什么", "哪些", "哪个", "能否", "是否",
    "可以", "一直", "总是", "情况", "办法", "问题", "一般", "需要", "想要", "能帮",
    # 故障类泛化词：登录失败/上传失败/支付失败都会用到，不足以定位具体业务
    "失败", "异常", "错误", "报错", "无法", "不能", "不了", "出来", "出现", "显示",
    "解决", "处理", "不好", "不行", "没用", "用不", "怎么了", "回事",
})

# 字段权重：标题最能代表文章主题，正文只作补充
FIELD_WEIGHTS: Tuple[Tuple[str, float], ...] = (
    ("title", 3.0),
    ("keywords", 2.4),
    ("summary", 1.6),
    ("body", 1.0),
)

_CJK_BLOCK = re.compile(r"[\u4e00-\u9fff]+")
_WORD = re.compile(r"[a-z0-9]+")

# 疑问字：含这些字的 2 字组合多为跨词碎片（如「失败怎么办」切出「败怎」），不能当实义词
INTERROGATIVE_CHARS = frozenset("怎什如何哪谁几多吗呢吧啊")

# 索引缓存（按语料指纹），最多保留 4 份，避免多实例切换时无限增长
_CACHE_LOCK = threading.Lock()
_CACHE: "OrderedDict[str, VectorIndex]" = OrderedDict()
_CACHE_MAX = 4


def tokenize(text: str) -> List[str]:
    """切词：中文取 2 字短语，英文数字取小写单词。"""
    if not text:
        return []
    lower = text.lower()
    tokens: List[str] = []
    for block in _CJK_BLOCK.findall(lower):
        # 单字单独成块时跳过：单个汉字歧义太大，不足以作为检索特征
        for i in range(len(block) - 1):
            gram = block[i:i + 2]
            if gram not in STOP_GRAMS:
                tokens.append(gram)
    tokens.extend(_WORD.findall(lower))
    return tokens


def _sublinear(tf: int) -> float:
    """词频次线性压缩：避免长正文靠重复词占优。"""
    return 1.0 + math.log(tf) if tf > 0 else 0.0


def _normalize(vector: Dict[str, float]) -> Dict[str, float]:
    """L2 归一化：归一化后点积即余弦相似度。"""
    norm = math.sqrt(sum(v * v for v in vector.values()))
    if norm == 0:
        return {}
    return {k: v / norm for k, v in vector.items()}


def _dot(a: Dict[str, float], b: Dict[str, float]) -> float:
    """稀疏向量点积：遍历较短的一方，减少无谓比较。"""
    if len(a) > len(b):
        a, b = b, a
    return sum(v * b.get(k, 0.0) for k, v in a.items())


def _fingerprint(docs: Sequence[KnowledgeItem]) -> str:
    """用语料内容生成指纹，作为索引缓存键。"""
    digest = hashlib.md5()
    for d in docs:
        digest.update(f"{d.id}\u0001{d.title}\u0001{d.summary}\u0001{d.keywords}\u0001{d.body}".encode("utf-8"))
    return f"{len(docs)}-{digest.hexdigest()}"


class VectorIndex:
    """知识文章向量索引：构建一次，反复检索。"""

    def __init__(self, docs: Sequence[KnowledgeItem]):
        self.docs = list(docs)
        raw_vectors: List[Dict[str, float]] = []
        df: Counter = Counter()

        for doc in self.docs:
            vector: Dict[str, float] = {}
            for field, weight in FIELD_WEIGHTS:
                counts = Counter(tokenize(getattr(doc, field, "") or ""))
                for term, tf in counts.items():
                    vector[term] = vector.get(term, 0.0) + weight * _sublinear(tf)
            for term in vector:
                df[term] += 1
            raw_vectors.append(vector)

        total = max(len(self.docs), 1)
        self.idf = {term: math.log((total + 1) / (cnt + 1)) + 1.0 for term, cnt in df.items()}
        self.vectors = [
            _normalize({term: weight * self.idf.get(term, 1.0) for term, weight in vector.items()})
            for vector in raw_vectors
        ]
        # 标题 + 关键词原文，用于短语命中的小幅加成（提问原样出现在标题里通常强相关）
        self.title_texts = [normalize((d.title or "") + (d.keywords or "")) for d in self.docs]
        # 标题 + 关键词 + 摘要：用于核心词校验，判断文章主题是否覆盖提问的实义词
        self.head_texts = [
            normalize((d.title or "") + (d.keywords or "") + (d.summary or "")) for d in self.docs
        ]

    def _core_grams(self, query: str) -> set:
        """提问的实义词集合：去掉停用词、泛化说法与疑问碎片，且必须出现在语料中。"""
        q_norm = normalize(query)
        if len(q_norm) < 2:
            return set()
        grams = {q_norm[i:i + 2] for i in range(len(q_norm) - 1)}
        return {
            g for g in grams
            if g not in STOP_GRAMS and g not in GENERIC_GRAMS and g in self.idf
            and not (set(g) & INTERROGATIVE_CHARS)
        }

    def search(self, query: str, top_k: int) -> List[Tuple[KnowledgeItem, float]]:
        """返回 (文章, 余弦相似度) 列表，按相似度降序。"""
        query_vector = self._query_vector(query)
        if not query_vector:
            return []

        q_norm = normalize(query)
        q_grams = {q_norm[i:i + 2] for i in range(len(q_norm) - 1)} if len(q_norm) >= 2 else set()
        core = self._core_grams(query)

        scored: List[Tuple[KnowledgeItem, float]] = []
        for idx, vector in enumerate(self.vectors):
            if not vector:
                continue
            # 核心词校验：实义词一个都没出现在标题/关键词/摘要里，说明主题不对口，直接排除
            if core and not any(g in self.head_texts[idx] for g in core):
                continue
            score = _dot(query_vector, vector)
            # 短语加成：问题原句出现在标题里，或标题里覆盖了提问的大部分短语
            title_text = self.title_texts[idx]
            if q_norm and len(q_norm) >= 2 and q_norm in title_text:
                score += 0.15
            elif q_grams:
                coverage = sum(1 for g in q_grams if g in title_text) / len(q_grams)
                if coverage >= 0.6:
                    score += 0.12 * coverage
            scored.append((self.docs[idx], min(score, 1.0)))

        scored.sort(key=lambda item: item[1], reverse=True)
        return scored[:top_k]

    def _query_vector(self, query: str) -> Dict[str, float]:
        """把问题编码成与文章同空间的 TF-IDF 向量（语料里没出现的词权重为 0）。"""
        counts = Counter(tokenize(query))
        if not counts:
            return {}
        weighted = {
            term: _sublinear(tf) * self.idf[term]
            for term, tf in counts.items() if term in self.idf
        }
        return _normalize(weighted)

    def coverage(self, query: str) -> float:
        """问题短语在语料中的覆盖率：衡量「知识库是否覆盖该问法」。"""
        q_norm = normalize(query)
        if len(q_norm) < 2:
            return 0.0
        grams = {q_norm[i:i + 2] for i in range(len(q_norm) - 1)}
        known = sum(1 for g in grams if g in self.idf)
        return known / len(grams)


def get_index(docs: Sequence[KnowledgeItem]) -> VectorIndex:
    """按语料指纹取索引，未命中则构建并缓存。"""
    key = _fingerprint(docs)
    with _CACHE_LOCK:
        cached = _CACHE.get(key)
        if cached is not None:
            _CACHE.move_to_end(key)
            return cached
    index = VectorIndex(docs)
    with _CACHE_LOCK:
        _CACHE[key] = index
        _CACHE.move_to_end(key)
        while len(_CACHE) > _CACHE_MAX:
            _CACHE.popitem(last=False)
    return index


def vector_search(query: str, docs: Sequence[KnowledgeItem], top_k: int = 5,
                  min_score: float = 0.12) -> List[Tuple[KnowledgeItem, float]]:
    """向量检索入口：返回达到相关度阈值的 top_k 篇文章及分数。"""
    if not docs:
        return []
    query = (query or "").strip()
    if len(normalize(query)) < 2:
        return []
    index = get_index(docs)
    return [(doc, score) for doc, score in index.search(query, top_k) if score >= min_score]


def describe(index: VectorIndex, query: str) -> Optional[Dict[str, float]]:
    """调试用：输出问题在语料中的覆盖率，便于排查检索不到的原因。"""
    if not query:
        return None
    return {"coverage": round(index.coverage(query), 4)}
