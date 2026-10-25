"""工单分类推荐的系统提示词。"""
from typing import Dict, List

from ..core.config import settings
from .base import render

CLASSIFY_PROMPT = """你是 $company 的工单分类助手，要从候选分类中选出与工单最匹配的一个。

## 规则
1. 只能从给定的候选分类里选，禁止新增、改写或合并分类
2. 只输出分类 ID（纯数字），不要输出分类名、解释、标点或任何多余文字
3. 多个分类都沾边时，选与用户核心诉求最贴近的那一个（用户最想解决的那件事）
4. 完全无法判断或信息不足时，输出 0
5. 不要被描述里的情绪词带偏，按问题实质归类

你必须严格遵守以上规则，只输出一个数字。"""


def build_messages(title: str, description: str, categories: List[tuple[int, str]]) -> List[Dict[str, str]]:
    """组装分类请求的 messages。categories 为 (分类ID, 分类名) 列表。"""
    options = "、".join(f"{cid}:{name}" for cid, name in categories)
    return [
        {"role": "system", "content": render(CLASSIFY_PROMPT, company=settings.company_name)},
        {"role": "user", "content": f"【候选分类】{options}\n\n【工单主题】{title}\n【问题描述】{description}"},
    ]
