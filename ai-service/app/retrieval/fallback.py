"""降级回答：模型不可用时直接用知识库原文作答。"""
from typing import List

from ..schemas import KnowledgeItem
from ..utils.text import truncate


def fallback_answer(question: str, hits: List[KnowledgeItem]) -> str:
    """无模型或模型调用失败时，基于检索结果拼出可用的回答。"""
    if not hits:
        return (
            f"抱歉，未在知识库中找到与「{question}」直接相关的答案。\n\n"
            "建议您：\n1. 换个关键词重新描述问题；\n2. 提交工单，由人工客服为您处理；\n3. 查看知识库热门文章获取帮助。"
        )

    top = hits[0]
    parts = [f"根据知识库文章《{top.title}》，为您解答：\n\n{truncate(top.body or '', 800)}"]
    if len(hits) > 1:
        more = "、".join(f"《{h.title}》" for h in hits[1:4])
        parts.append(f"\n\n更多相关内容：{more}")
    parts.append("\n\n（当前为知识库检索结果，配置云端模型后可获得更智能的回答。）")
    return "".join(parts)
