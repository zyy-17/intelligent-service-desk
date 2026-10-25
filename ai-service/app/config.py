"""FastAPI AI 服务配置。"""
import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass
class Settings:
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))
    ai_enabled: bool = os.getenv("AI_ENABLED", "true").lower() == "true"
    ai_base_url: str = os.getenv("AI_BASE_URL", "https://api.deepseek.com/v1")
    ai_api_key: str = os.getenv("AI_API_KEY", "")
    ai_model: str = os.getenv("AI_MODEL", "deepseek-chat")
    ai_timeout_seconds: int = int(os.getenv("AI_TIMEOUT_SECONDS", "45"))
    ai_temperature: float = float(os.getenv("AI_TEMPERATURE", "0.3"))
    # Redis（会话存储等临时数据；不可用时自动降级为进程内存存储）
    redis_host: str = os.getenv("REDIS_HOST", "192.168.100.128")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))
    redis_db: int = int(os.getenv("REDIS_DB", "1"))
    redis_password: str = os.getenv("REDIS_PASSWORD", "") or None
    redis_key_prefix: str = os.getenv("REDIS_KEY_PREFIX", "isd:ai")
    # 会话保留时长（秒），默认 7 天
    session_ttl_seconds: int = int(os.getenv("SESSION_TTL_SECONDS", "604800"))
    # 单个会话最多保留的消息条数
    session_max_messages: int = int(os.getenv("SESSION_MAX_MESSAGES", "100"))


settings = Settings()
