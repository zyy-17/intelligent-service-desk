"""AI 服务启动入口：在 ai-service 目录下执行 `python main.py` 即可启动。

等价于 `uvicorn app.main:app --host 0.0.0.0 --port 8000`。
可用环境变量覆盖：AI_HOST（默认 0.0.0.0）、AI_SERVICE_PORT（默认 8000）、AI_RELOAD（默认 false）。
注意：需使用项目虚拟环境中的 Python（.venv），否则缺少依赖会降级运行。
"""
import os
import sys
from pathlib import Path

# 保证在任意工作目录下执行都能定位到 app 包（以本文件所在目录为项目根）
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> None:
    """按环境变量与配置文件启动 uvicorn 服务"""
    import uvicorn

    from app.core.config import settings

    host = os.getenv("AI_HOST", "0.0.0.0")
    reload_flag = os.getenv("AI_RELOAD", "false").lower() in ("1", "true", "yes")
    print(f"[ai-service] 启动于 http://{host}:{settings.port}  文档: http://localhost:{settings.port}/docs")
    uvicorn.run("app.main:app", host=host, port=settings.port, reload=reload_flag)


if __name__ == "__main__":
    main()
