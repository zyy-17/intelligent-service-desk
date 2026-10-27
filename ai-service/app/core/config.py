"""AI 服务配置：从环境变量读取端口、模型、Redis、角色设定等参数。"""
import os
from dataclasses import dataclass
from typing import List

from dotenv import load_dotenv

load_dotenv()


def _split_models(raw: str) -> List[str]:
    """把逗号分隔的模型名拆成列表，去空白与空项。"""
    return [m.strip() for m in (raw or "").split(",") if m.strip()]


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
    # 云端候选模型（逗号分隔，越靠前越优先；留空则只用 ai_model 单个）
    ai_models_raw: str = os.getenv("AI_MODELS", "")
    ai_timeout_seconds: int = int(os.getenv("AI_TIMEOUT_SECONDS", "45"))
    ai_temperature: float = float(os.getenv("AI_TEMPERATURE", "0.3"))

    # ===== 本地 Ollama 兜底（走 OpenAI 兼容的 /v1 端点，与云端同一套调用代码）=====
    # 注意：不能用 OLLAMA_MODELS —— 那是 Ollama 官方的「模型文件存放目录」变量，
    # 本机已存在该变量（如 D:\develop\ollama\ollama_models），会与「候选模型列表」混淆。
    ollama_enabled: bool = os.getenv("OLLAMA_CHAT_ENABLED", "true").lower() == "true"
    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434/v1")
    ollama_api_key: str = os.getenv("OLLAMA_API_KEY", "ollama")
    # Ollama 候选模型（逗号分隔，越靠前越优先；前一个失败自动换下一个）
    ollama_models_raw: str = os.getenv("OLLAMA_CHAT_MODELS", "qwen2.5-coder:7b,deepseek-r1:1.5b")
    ollama_timeout_seconds: int = int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "180"))
    ollama_temperature: float = float(os.getenv("OLLAMA_TEMPERATURE", "0.3"))

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

    @property
    def ai_model_list(self) -> List[str]:
        """云端候选模型：AI_MODELS 优先，未配置时退回单个 AI_MODEL。"""
        return _split_models(self.ai_models_raw) or [self.ai_model]

    @property
    def ollama_model_list(self) -> List[str]:
        """Ollama 候选模型列表，按顺序尝试。"""
        return _split_models(self.ollama_models_raw)


settings = Settings()
