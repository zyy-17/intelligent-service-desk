"""FastAPI AI 服务入口（仅负责应用装配）。

业务能力分布：
    api/        路由层      —— 参数校验与 HTTP 形态
    services/   业务层      —— 问答编排、会话、工单辅助
    prompts/    提示词层    —— 各场景系统角色提示词
    llm/        模型层      —— OpenAI 兼容接口调用
    retrieval/  检索层      —— 本地知识匹配与降级
    storage/    存储层      —— 会话持久化（Redis / 内存）
Java 业务后端作为主控方，通过 HTTP 调用本服务。
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .core.config import settings
from .core.logging_setup import setup_logging

# 必须先初始化日志：业务模块（如 storage 层的 SessionStore）在 import 时就连接 Redis 并打日志，
# 若晚于业务 import，这些日志会走 logging 的兜底 handler，丢失时间戳与级别，不利于排查。
setup_logging()

from .api import all_routers  # noqa: E402  依赖上面的日志初始化，必须后置

app = FastAPI(
    title="Intelligent Service Desk AI Service",
    description="智能客服与工单管理平台的 AI 辅助服务",
    version="1.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

for router in all_routers:
    app.include_router(router)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=False)
