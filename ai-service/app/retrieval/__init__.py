"""检索层：知识匹配、上下文构建与降级回答。"""
from .fallback import fallback_answer
from .search import build_context, search_knowledge

__all__ = ["search_knowledge", "build_context", "fallback_answer"]
