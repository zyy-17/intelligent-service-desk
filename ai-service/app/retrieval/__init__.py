"""检索层：向量检索、上下文构建、引用整理与降级回答。"""
from .fallback import fallback_answer
from .search import build_context, search_knowledge, to_references
from .vector import vector_search

__all__ = ["search_knowledge", "build_context", "to_references", "fallback_answer", "vector_search"]
