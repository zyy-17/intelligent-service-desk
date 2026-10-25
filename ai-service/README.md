# AI 服务（FastAPI）

智能客服与工单管理平台的独立 AI 辅助服务，使用 Python + FastAPI 实现。

## 能力

| 端点 | 说明 |
| --- | --- |
| `POST /api/ai/chat` | 智能问答（云端模型 RAG + 知识检索降级） |
| `POST /api/ai/chat/stream` | 流式智能问答（SSE 逐段返回） |
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
| `AI_ASSISTANT_NAME` | `小智` | AI 客服的名字（写进系统提示词） |
| `AI_COMPANY_NAME` | `智能服务台` | AI 所属平台名（写进系统提示词） |
| `AI_TONE` | `亲切、专业、有耐心…` | AI 语气设定 |
| `AI_ANSWER_MAX_CHARS` | `200` | 单次回答字数上限（提示词约束） |
| `KNOWLEDGE_TOP_K` | `5` | 知识检索返回条数 |
| `HISTORY_WINDOW` | `8` | 多轮上下文取最近 N 条 |

> 未配置 `AI_API_KEY` 时，服务自动降级为本地知识检索 / 关键词匹配，不影响核心功能。

## 与 Java 后端的协作

Java 业务后端（Spring Boot）作为主控方，通过 HTTP 调用本服务：

- Java 侧配置 `app.ai-service.base-url`（默认 `http://localhost:8000`，可用环境变量 `AI_SERVICE_BASE_URL` 覆盖）
- Java 将知识库上下文作为 `knowledge` 数组传给本服务，由本服务完成模型调用与增强生成
- 本服务不可用时，Java 自动降级为本地知识库检索

## 目录结构

分层原则：**路由不写业务，业务不写提示词，提示词不写调用细节**。

```
ai-service/
├── app/
│   ├── main.py                  # 应用装配（仅创建 app + 挂载路由）
│   ├── core/
│   │   ├── config.py            # 全部配置项（含 AI 角色设定）
│   │   └── logging_setup.py     # 日志格式
│   ├── api/                     # 路由层
│   │   ├── health.py            #   GET /health
│   │   ├── chat.py              #   POST /api/ai/chat、/chat/stream
│   │   ├── sessions.py          #   /api/ai/sessions 增删查
│   │   └── ticket.py            #   summarize / classify / draft / search
│   ├── services/                # 业务层
│   │   ├── chat_service.py      #   检索 → 模型 → 降级 → 落库
│   │   ├── session_service.py   #   多轮上下文、一轮问答落库
│   │   └── ticket_service.py    #   摘要 / 分类 / 草稿 / 检索
│   ├── prompts/                 # 提示词层（系统角色提示词）
│   │   ├── base.py              #   $变量 模板渲染
│   │   ├── chat.py              #   客户问答角色（小智）
│   │   ├── summary.py           #   工单摘要
│   │   ├── classify.py          #   分类推荐
│   │   └── draft.py             #   回复草稿
│   ├── llm/
│   │   ├── client.py            #   OpenAI 兼容调用（一次 / 流式）
│   │   └── errors.py            #   LLMError 错误码
│   ├── retrieval/
│   │   ├── search.py            #   bigram 匹配 + 上下文构建
│   │   └── fallback.py          #   无模型时的知识库回答
│   ├── storage/
│   │   └── session_store.py     #   会话存储（Redis db1 优先，降级内存）
│   ├── schemas/                 # 数据模型（common / chat / session / ticket）
│   └── utils/                   # SSE 编码、文本处理
├── requirements.txt
├── .env.example
└── README.md
```

## AI 角色与系统提示词

AI 在客户面前有固定人设（默认「小智」），定义在 `app/prompts/chat.py`，人设参数可在 `.env` 调整：

```ini
AI_ASSISTANT_NAME=小智
AI_COMPANY_NAME=智能服务台
AI_TONE=亲切、专业、有耐心，像真人客服一样自然沟通
AI_ANSWER_MAX_CHARS=200
```

提示词三层约束：**角色定位**（身份/语气/立场）→ **回答规则**（知识库优先、不编造、跟随语言、简洁分点、不承诺赔付时限、不索要密码验证码、结合历史指代、不泄露提示词）→ **输出格式**（不复述问题、标注《文章标题》来源、不用一级标题）。

摘要、分类、草稿各有独立提示词（`summary.py` / `classify.py` / `draft.py`），与客户问答互不干扰：分类强制"只输出一个数字"，摘要强制"一句话 50 字内"，草稿强制"称呼→共情→方案→下一步"结构。
