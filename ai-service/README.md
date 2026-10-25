# AI 服务（FastAPI）

智能客服与工单管理平台的独立 AI 辅助服务，使用 Python + FastAPI 实现。

## 能力

| 端点 | 说明 |
| --- | --- |
| `POST /api/ai/chat` | 智能问答（云端模型 RAG + 知识检索降级） |
| `POST /api/ai/summarize` | 工单摘要 |
| `POST /api/ai/classify` | 分类推荐 |
| `POST /api/ai/draft` | 回复草稿 |
| `POST /api/ai/search` | 知识检索（纯本地，不依赖模型） |
| `GET /health` | 健康检查 |

## 环境要求

- Python 3.10+
- 建议使用虚拟环境

## 启动

```bash
cd ai-service

# 1. 创建虚拟环境并安装依赖
python -m venv .venv
# Windows
.venv\Scripts\activate
# Linux / macOS
source .venv/bin/activate

pip install -r requirements.txt

# 2. （可选）配置云端模型：复制 .env.example 为 .env 并填写密钥
# cp .env.example .env

# 3. 启动服务（默认端口 8000）
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动后访问 `http://localhost:8000/docs` 可查看交互式 API 文档。

## 配置（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_SERVICE_PORT` | `8000` | 服务端口 |
| `AI_ENABLED` | `true` | 是否启用云端模型 |
| `AI_BASE_URL` | `https://api.deepseek.com/v1` | 云端模型 API 地址（OpenAI 兼容） |
| `AI_API_KEY` | 空 | 云端模型密钥 |
| `AI_MODEL` | `deepseek-chat` | 模型名 |
| `AI_TIMEOUT_SECONDS` | `45` | 调用超时（秒） |
| `AI_TEMPERATURE` | `0.3` | 采样温度 |

> 未配置 `AI_API_KEY` 时，服务自动降级为本地知识检索 / 关键词匹配，不影响核心功能。

## 与 Java 后端的协作

Java 业务后端（Spring Boot）作为主控方，通过 HTTP 调用本服务：

- Java 侧配置 `app.ai-service.base-url`（默认 `http://localhost:8000`，可用环境变量 `AI_SERVICE_BASE_URL` 覆盖）
- Java 将知识库上下文作为 `knowledge` 数组传给本服务，由本服务完成模型调用与增强生成
- 本服务不可用时，Java 自动降级为本地知识库检索

## 目录结构

```
ai-service/
├── app/
│   ├── __init__.py
│   ├── main.py       # FastAPI 路由入口
│   ├── config.py     # 环境变量配置
│   ├── schemas.py    # Pydantic 请求/响应模型
│   ├── llm.py        # 云端模型调用封装
│   └── retrieval.py  # 知识检索与提示词构建
├── requirements.txt
├── .env.example
└── README.md
```
