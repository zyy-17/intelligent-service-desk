"""健康检查路由。"""
from fastapi import APIRouter

from ..llm import is_configured
from ..storage import store

router = APIRouter(tags=["系统"])


@router.get("/health")
def health():
    """返回服务状态、模型配置与 Redis 可用性。"""
    return {"status": "ok", "model_configured": is_configured(), "redis": store.redis_available}
