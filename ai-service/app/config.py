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


settings = Settings()
