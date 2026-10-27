"""AI 服务配置：从环境变量读取端口、模型、Redis、角色设定等参数。"""
import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass
class Settings:
    """AI 服务全部配置项，均可通过环境变量覆盖。"""

    # ===== 服务 =====
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))

    # ===== 云端大模型（OpenAI 兼容接口）=====
    ai_enabled: bool = os.getenv("AI_ENABLED", "true").lower() == "true"
    ai_base_url: str = os.getenv("AI_BASE_URL", "https://api.deepseek.com/v1")
    ai_api_key: str = os.getenv("AI_API_KEY", "")
    ai_model: str = os.getenv("AI_MODEL", "deepseek-chat")
    ai_timeout_seconds: int = int(os.getenv("AI_TIMEOUT_SECONDS", "45"))
    ai_temperature: float = float(os.getenv("AI_TEMPERATURE", "0.3"))

    # ===== AI 角色设定（决定系统提示词里的人设，改这里就能换人设，不用改代码）=====
    assistant_name: str = os.getenv("AI_ASSISTANT_NAME", "小智")
    company_name: str = os.getenv("AI_COMPANY_NAME", "智能服务台")
    tone: str = os.getenv("AI_TONE", "亲切、专业、有耐心，像真人客服一样自然沟通")
    answer_max_chars: int = int(os.getenv("AI_ANSWER_MAX_CHARS", "200"))

    # ===== 问答策略 =====
    knowledge_top_k: int = int(os.getenv("KNOWLEDGE_TOP_K", "5"))
    history_window: int = int(os.getenv("HISTORY_WINDOW", "8"))
    # 向量检索的余弦相似度阈值：低于该值视为「知识库没有可靠依据」
    # 配合 vector.py 的核心词校验，可压到 0.10：主题不对口的文章已被提前排除，不会误引用
    retrieval_min_score: float = float(os.getenv("RETRIEVAL_MIN_SCORE", "0.10"))
    # 单篇知识正文拼进上下文的长度上限
    knowledge_context_chars: int = int(os.getenv("KNOWLEDGE_CONTEXT_CHARS", "700"))

    # ===== Redis（会话存储；不可用时自动降级为进程内存）=====
    redis_host: str = os.getenv("REDIS_HOST", "192.168.100.128")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))
    redis_db: int = int(os.getenv("REDIS_DB", "1"))
    redis_password: str = os.getenv("REDIS_PASSWORD", "") or None
    redis_key_prefix: str = os.getenv("REDIS_KEY_PREFIX", "isd:ai")

    # ===== 会话保留策略 =====
    session_ttl_seconds: int = int(os.getenv("SESSION_TTL_SECONDS", "604800"))
    session_max_messages: int = int(os.getenv("SESSION_MAX_MESSAGES", "100"))


settings = Settings()
