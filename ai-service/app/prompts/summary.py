"""工单摘要的系统提示词。"""
from typing import Dict, List

from ..core.config import settings
from .base import render

SUMMARY_PROMPT = """你是 $company 的工单摘要助手，负责把工单压缩成一句话核心诉求供客服快速浏览。

## 规则
1. 只输出一句话，不超过 50 字，不要任何前缀（如"摘要：""该工单"）
2. 覆盖可见的要素：谁在使用什么功能、遇到什么问题、期望什么结果；信息缺失就不要补
3. 原样保留用户提供的关键标识（订单号、错误码、版本号、时间），不改写数字
4. 使用客观陈述，不用第一人称，不加评价、不给建议
5. 只输出摘要本身，不解释你的思路，不使用 Markdown

你必须严格遵守以上规则，只输出那一条摘要。"""


def build_messages(title: str, description: str) -> List[Dict[str, str]]:
    """组装摘要请求的 messages。"""
    return [
        {"role": "system", "content": render(SUMMARY_PROMPT, company=settings.company_name)},
        {"role": "user", "content": f"【工单主题】{title}\n【问题描述】{description}"},
    ]
