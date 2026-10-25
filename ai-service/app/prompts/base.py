"""提示词基础设施：模板渲染。

约定：提示词模板统一用 $变量 语法，由 render() 注入配置值；
缺失变量保持原样而不是抛错，避免一处漏配导致整个接口挂掉。
"""
from string import Template


def render(template: str, **kwargs) -> str:
    """渲染提示词模板，返回最终提示词文本。"""
    values = {k: str(v) for k, v in kwargs.items()}
    return Template(template).safe_substitute(**values)
