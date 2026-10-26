"""工单处置建议的系统提示词（客服侧右栏 AI Copilot）。"""
from typing import Dict, List

from ..core.config import settings
from .base import render

SUGGEST_PROMPT = """你是 $company 的资深客服，正在协助一线客服处理一张工单，需要给出可立即执行的处理步骤。

## 规则
1. 输出 2 到 3 步，每步一行，格式严格为：序号. 动作标题｜具体做法
   示例：1. 确认故障范围｜询问客户是单台设备还是整个部门受影响
2. 动作标题不超过 15 字，必须是一个具体动作，不要写"了解情况""沟通一下"这类空话
3. 具体做法不超过 60 字，写清做什么、怎么做，能落到操作层面
4. 优先依据【知识库参考】里的方案；知识库没有依据时给通用排查思路，但不得编造产品功能、界面名称或政策条款
5. 第一步通常是获取判断依据（确认现象、复现条件、报错信息），最后一步要指向解决或升级
6. 不称呼客户姓名，不写开场白与结尾，不写"作为客服"之类的自我描述
7. 不承诺赔付、时限、退款等具体结果
8. 只输出步骤列表本身，不用 Markdown 标题、不用代码块、不加任何解释

你必须严格遵守以上规则，只输出那几行步骤。"""

FALLBACK_STEPS = [
    ("确认问题现象", "向客户询问复现步骤、发生时间与报错截图，判断影响范围是单人还是全员"),
    ("检索历史同类工单", "查看该客户近期工单与知识库同类问题，确认是否为已知问题及既有解决方案"),
    ("给出方案或升级", "有明确方案则说明操作步骤与预计生效时间，无方案则转交二线并告知客户处理进度"),
]


def build_messages(title: str, description: str, category: str, context: str) -> List[Dict[str, str]]:
    """组装处置建议请求的 messages。"""
    user = (
        f"【工单分类】{category or '未分类'}\n"
        f"【工单主题】{title}\n"
        f"【问题描述】{description}\n"
        f"【知识库参考】{context or '无匹配文章'}"
    )
    return [
        {"role": "system", "content": render(SUGGEST_PROMPT, company=settings.company_name)},
        {"role": "user", "content": user},
    ]
