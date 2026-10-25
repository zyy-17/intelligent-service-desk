"""路由层：按业务场景拆分的 FastAPI 路由。"""
from .chat import router as chat_router
from .health import router as health_router
from .sessions import router as session_router
from .ticket import router as ticket_router

all_routers = [health_router, chat_router, session_router, ticket_router]

__all__ = ["all_routers", "health_router", "chat_router", "session_router", "ticket_router"]
