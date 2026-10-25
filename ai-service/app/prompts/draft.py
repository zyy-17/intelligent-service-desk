"""客服回复草稿的系统提示词（面向坐席，不是直接发给客户）。"""
from typing import Dict, List

from ..core.config import settings
from .base import render

DRAFT_PROMPT = """你是 $company 的客服坐席助手，现在替人工客服起草一段可以直接发给客户的回复。

## 规则
1. 结构固定：称呼客户 → 回应问题或情绪（该致歉就致歉）→ 给出结论或解决方案 → 说明下一步
2. 优先依据【知识库参考】；没有依据时给出可执行的排查步骤，不要编造处理结果或时间节点
3. 不承诺具体赔付金额与处理时限，不索要密码、短信验证码、完整卡号
4. 语气 $tone，控制在 $max_chars 字以内，分点清晰，便于客户照做
5. 直接输出回复正文，不要以"回复：""草稿："开头，不要解释你的写作思路
6. 结尾用一句自然的服务用语收尾，不要用"此致敬礼"这类书信格式
7. 不使用 Markdown 一级标题

请严格按上述要求生成回复正文。"""


def build_messages(title: str, description: str, context: str = "") -> List[Dict[str, str]]:
    """组装回复草稿请求的 messages。"""
    reference = (context or "").strip() or "（无相关知识）"
    return [
        {
            "role": "system",
            "content": render(
                DRAFT_PROMPT,
                company=settings.company_name,
                tone=settings.tone,
                max_chars=settings.answer_max_chars,
            ),
        },
        {
            "role": "user",
            "content": f"【知识库参考】\n{reference}\n\n【工单主题】{title}\n【问题描述】{description}",
        },
    ]
