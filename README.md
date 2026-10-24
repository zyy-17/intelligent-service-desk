# Intelligent Service Desk 智能客服与工单管理平台

基于 **Spring Boot + Vue 3 + MySQL** 的智能客户服务与工单管理平台。以工单为核心，打通「客户提交问题 → 客服受理处理 → 主管分配监控 → 知识库与 AI 辅助」的完整服务闭环。

> 项目名称：intelligent-service-desk（原代号 NexusDesk 已全面替换）

---

## ✨ 功能特性

| 模块 | 说明 |
| --- | --- |
| 工作台看板 | KPI 指标、工单趋势、分类占比、最新工单、热门知识、AI 助手、服务概况 |
| 工单管理 | 创建、查询、接单、分配、转派、回复、提交解决方案、客户确认、关闭/重开、评价 |
| 客户管理 | 用户列表、角色、状态管理 |
| 知识库 | 文章分类、编辑、审核、发布、检索、热门排行 |
| 智能问答 | 云端大模型（OpenAI 兼容）RAG + 知识库检索降级，保证离线可演示 |
| 数据统计 | 状态/优先级/满意度分布、客服工作量、趋势图 |
| 系统管理 | 用户角色、工单分类、SLA 配置、操作审计日志 |
| 安全 | JWT 认证、基于角色的接口级 + 资源级权限校验、PBKDF2 密码哈希、内部备注隔离 |

---

## 🏗️ 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 25、Spring Boot 4.1.2、Spring Data JDBC、MySQL Connector/J |
| 前端 | Vue 3、Vite 6、Element Plus、ECharts、Pinia、Vue Router、Axios |
| 数据库 | MySQL 8.0 |
| AI | 云端大模型（OpenAI 兼容接口：DeepSeek / 通义百炼 / Kimi / 智谱 / OpenAI 等），支持知识库检索降级 |

> 架构说明：按需求文档采用「模块化单体」起步（第一阶段），后端按 `controller / service / repository / entity / security / config` 分层，业务边界清晰，为后续演进至 Spring Cloud 微服务预留了模块边界。

---

## 📁 目录结构

```
intelligent-service-desk/
├── src/
│   ├── main/
│   │   ├── java/com/zyyqq/desk/
│   │   │   ├── common/        # 统一响应、分页、异常处理
│   │   │   ├── config/        # 配置、数据初始化(DataSeeder)
│   │   │   ├── controller/    # REST 接口层
│   │   │   ├── entity/        # 实体
│   │   │   ├── repository/    # 数据访问层
│   │   │   ├── security/      # JWT、密码哈希、认证拦截
│   │   │   └── service/       # 业务逻辑层
│   │   └── resources/
│   │       ├── application.yml   # 主配置
│   │       ├── db/schema.sql     # 建表脚本
│   │       └── static/           # 前端构建产物
│   └── test/
├── frontend/                  # Vue 3 前端源码
│   ├── src/views/             # 7 个页面 + 登录页
│   ├── src/layout/            # 主布局
│   ├── src/api/               # Axios 封装
│   ├── src/store/             # Pinia 状态
│   └── src/router/            # 路由
├── pom.xml
└── README.md
```

---

## 🚀 快速启动

### 环境要求

- **JDK 21+**（本项目使用 JDK 25）
- **Maven 3.9+**
- **MySQL 8.0+**（本机 3306 端口，root 账号）
- **Node.js 20+**（仅前端开发/构建时需要）

### 1. 配置数据库

默认连接本机 MySQL：

```
host: localhost:3306
database: intelligent_service_desk（首次启动自动创建）
username: root
password: 123456
```

如需修改，编辑 `src/main/resources/application.yml`，或通过环境变量覆盖：

```bash
# Windows (PowerShell)
$env:DB_USERNAME="root"; $env:DB_PASSWORD="你的密码"
# Linux / macOS
export DB_USERNAME=root DB_PASSWORD=你的密码
```

> 数据库表结构（`db/schema.sql`）会在启动时自动执行，演示数据（1286 条工单、用户、知识文章等）也会自动初始化，无需手动建库。

### 2. 启动后端

```bash
mvn spring-boot:run
# 或打包运行
mvn package -DskipTests
java -jar target/intelligent-service-desk-0.0.1-SNAPSHOT.jar
```

后端默认运行在 `http://localhost:8080`。

### 3. 前端（二选一）

**方式 A：直接使用内置前端**（推荐，无需 Node）

后端启动时已包含前端构建产物，直接访问 `http://localhost:8080` 即可。

**方式 B：开发模式**

```bash
cd frontend
npm install
npm run dev          # 开发服务器 http://localhost:5173（自动代理 /api 到 8080）
npm run build        # 构建产物输出到 src/main/resources/static
```

---

## 🔑 测试账号

所有账号密码均为 **123456**：

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| admin | 系统管理员 | 全部权限 |
| supervisor | 客服主管 | 分配/转派、查看统计 |
| agent1 ~ agent8 | 客服人员 | 接单、处理工单 |
| customer1 ~ customer10 | 客户 | 提交工单、评价 |

---

## 🤖 配置云端大模型（可选）

智能问答默认走「知识库检索」兜底，无需任何配置即可使用。若想获得更智能的回答，配置 OpenAI 兼容接口：

编辑 `application.yml` 的 `app.ai` 段，或通过环境变量注入：

```yaml
app:
  ai:
    enabled: true
    base-url: ${AI_BASE_URL:https://api.deepseek.com/v1}   # 你的 API 地址
    api-key: ${AI_API_KEY:}                                 # 你的密钥
    model: ${AI_MODEL:deepseek-chat}                        # 模型名
```

```bash
export AI_BASE_URL=https://api.deepseek.com/v1
export AI_API_KEY=sk-xxxx
export AI_MODEL=deepseek-chat
```

> 密钥通过环境变量注入，不会写入 Git 仓库。未配置密钥或调用失败时自动降级为知识库检索，不影响核心功能。

---

## 📡 主要 REST API

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/login` | POST | 登录 |
| `/api/auth/register` | POST | 客户注册 |
| `/api/tickets` | GET/POST | 工单列表 / 创建 |
| `/api/tickets/{id}` | GET | 工单详情（含消息、历史） |
| `/api/tickets/{id}/assign` | POST | 分配/转派 |
| `/api/tickets/{id}/accept` | POST | 接单 |
| `/api/tickets/{id}/messages` | POST | 回复 / 内部备注 |
| `/api/tickets/{id}/resolve` | POST | 提交解决方案 |
| `/api/tickets/{id}/confirm` | POST | 客户确认 |
| `/api/tickets/{id}/close` | POST | 关闭 |
| `/api/tickets/{id}/reopen` | POST | 重新打开 |
| `/api/tickets/{id}/rate` | POST | 评价 |
| `/api/knowledge` | GET/POST | 知识文章 |
| `/api/ai/chat` | POST | 智能问答 |
| `/api/stats/overview` | GET | 看板聚合统计 |
| `/api/admin/users` | GET | 用户管理 |
| `/api/admin/logs` | GET | 审计日志 |

统一响应格式：`{ "code": 0, "message": "success", "data": ... }`

---

## 🗄️ 数据模型

核心表：`sys_user`、`ticket`、`ticket_message`、`ticket_history`、`ticket_category`、`knowledge_article`、`knowledge_category`、`notification`、`operation_log`、`ai_task_log`。

工单状态机：`待分配(PENDING) → 待受理(ASSIGNED) → 处理中(PROCESSING) → 待客户确认(WAITING) → 已解决(RESOLVED) → 已关闭(CLOSED)`，支持重新打开。

---

## 🔒 安全说明

- 密码使用 PBKDF2（12 万次迭代）哈希存储，不明文保存
- JWT（HS256）认证，账号禁用后立即失效
- 接口级角色校验 + 资源级权限校验（客户只能访问自己的工单）
- 内部备注对客户接口完全隔离（后端过滤，非前端隐藏）
- 敏感操作（禁用账号、改角色、改分类）写入审计日志

---

## ⚠️ 已知限制

- 第一版为「模块化单体」，尚未拆分 Spring Cloud 微服务（Gateway/Nacos/Feign 为第二阶段目标）
- 附件上传、WebSocket 实时推送、Redis 缓存为后续扩展项
- AI 功能需配置云端模型密钥；未配置时走知识库检索

---

## 📄 许可证

课程设计项目，仅供学习与演示使用。
