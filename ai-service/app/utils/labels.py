"""枚举值的中文文案映射（角色 / 工单状态 / 优先级）。"""

ROLE_LABELS = {
    "CUSTOMER": "客户",
    "AGENT": "客服人员",
    "SUPERVISOR": "客服主管",
    "ADMIN": "系统管理员",
}

STATUS_LABELS = {
    "PENDING": "待处理",
    "ASSIGNED": "已派单",
    "PROCESSING": "处理中",
    "WAITING": "待客户回复",
    "RESOLVED": "已解决",
    "CLOSED": "已关闭",
}

PRIORITY_LABELS = {
    "URGENT": "紧急",
    "HIGH": "高",
    "MEDIUM": "中",
    "LOW": "低",
}


def role_label(value: str | None) -> str:
    """角色枚举转中文。"""
    return ROLE_LABELS.get(value or "", value or "")


def status_label(value: str | None) -> str:
    """工单状态枚举转中文。"""
    return STATUS_LABELS.get(value or "", value or "")


def priority_label(value: str | None) -> str:
    """优先级枚举转中文。"""
    return PRIORITY_LABELS.get(value or "", value or "")
