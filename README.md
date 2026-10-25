# 🤖 Intelligent Service Desk — 智能客服与工单管理平台

<p align="center">
  <b>基于 Spring Boot + Vue 3 + FastAPI + Redis 的全栈智能客服平台</b><br/>
  智能工单 · 知识驱动 · AI 赋能 · 多角色协作
</p>

---

## 📖 目录

- [项目简介](#-项目简介)
- [项目背景](#-项目背景)
- [技术栈](#-技术栈)
- [功能模块](#-功能模块)
- [项目结构](#-项目结构)
- [数据库设计](#-数据库设计)
- [前端设计](#-前端设计)
- [后端 API 接口](#-后端-api-接口)
- [AI 服务详解](#-ai-服务详解)
- [Redis 缓存设计](#-redis-缓存设计)
- [快速开始](#-快速开始)
- [默认账号](#-默认账号)
- [安全说明](#-安全说明)

---

## 📝 项目简介

Intelligent Service Desk 是一个面向企业的 **AI 驱动智能客服与工单管理平台**，以工单为核心，打通「客户提交问题 → 客服受理处理 → 主管分配监控 → 知识库与 AI 辅助」的完整服务闭环：

- 🎫 **工单全生命周期**：创建 → 分配 → 接单 → 处理 → 提交方案 → 客户确认 → 关闭 / 重开，完整状态机流转
- 👥 **多角色协作**：客户 / 客服人员 / 客服主管 / 系统管理员四种身份，各自拥有独立的功能与数据权限
- 📚 **知识库管理**：文章分类、编辑、审核、发布、热门排行、全文检索
- 🤖 **AI 智能问答**：云端大模型（OpenAI 兼容）RAG + 知识库检索降级，支持多轮会话上下文
- 📊 **数据统计**：状态 / 优先级 / 满意度分布、客服工作量、趋势图
- ⚙️ **系统管理**：用户角色、工单分类、审计日志
- 🛡️ **安全防护**：JWT 认证、接口级 + 资源级权限校验、PBKDF2 密码哈希、内部备注隔离、接口限流、工单幂等

---

## 🌍 项目背景

传统客服系统存在以下痛点，本项目逐一解决：

| 痛点                     | 本项目解决方案                                               |
| ------------------------ | ------------------------------------------------------------ |
| 工单流转不规范、易遗漏   | 完整状态机 + 操作历史留痕，每步状态变更可追溯                 |
| 客户诉求与客服脱节       | 客户侧提交/确认/评价，客服侧接单/处理/解决，双向闭环         |
| 重复问题反复解答         | 知识库沉淀常见问题 + AI 智能问答，秒级响应                   |
| 客服工作无法量化         | 数据统计（工作量/满意度/趋势）多维看板                       |
| 权限混乱、越权访问       | 基于角色的接口级 + 资源级双重校验，客户只能看到自己的工单     |
| 高峰并发与重复提交       | Redis 限流 + 工单 `Idempotency-Key` 幂等，保护核心接口        |
| 缺乏智能辅助             | FastAPI 独立 AI 服务：问答/摘要/分类/回复草稿，降级可离线演示 |

---

## 🛠️ 技术栈

### 后端

| 技术            | 版本    | 说明                                  |
| --------------- | ------- | ------------------------------------- |
| Java            | 25      | 编程语言                              |
| Spring Boot     | 4.1.2   | 应用框架                              |
| Spring Data JDBC| —       | 轻量 ORM 持久层                       |
| MySQL Connector | 9.x     | 数据库驱动                            |
| MySQL           | 8.0     | 关系型数据库                          |
| JWT (jjwt)      | —       | 无状态认证令牌（HS256）               |
| PBKDF2          | —       | 密码哈希（12 万次迭代）               |
| Spring Data Redis| —      | Redis 缓存/限流/幂等（Lettuce 连接池）|
| Jackson 3       | —       | JSON 序列化（`tools.jackson.databind`）|
| Lombok          | —       | 减少样板代码                          |

### 前端

| 技术         | 版本   | 说明                             |
| ------------ | ------ | -------------------------------- |
| Vue          | 3.x    | 渐进式前端框架                   |
| Vue Router   | 4.x    | 路由管理（嵌套路由 + meta 参数） |
| Pinia        | 2.x    | 状态管理                         |
| Element Plus | 2.x    | UI 组件库                        |
| ECharts      | 5.x    | 数据可视化                       |
| Axios        | 1.x    | HTTP 客户端                      |
| Vite         | 6.x    | 构建工具                         |

### AI 服务

| 技术       | 版本   | 说明                                               |
| ---------- | ------ | -------------------------------------------------- |
| Python     | 3.12   | 编程语言（统一使用 3.12）                          |
| FastAPI    | 0.115+ | 高性能 Web 框架                                    |
| Uvicorn    | 0.34+  | ASGI 服务器                                        |
| Pydantic   | 2.10+  | 数据校验                                           |
| HTTPX      | 0.28+  | 调用云端大模型 API（OpenAI 兼容）                  |
| Redis (py) | 5.2+   | AI 会话存储（db1，降级内存）                       |
| 云端 API   | —      | DeepSeek / 通义 / Kimi / 智谱 等 OpenAI 兼容接口   |

### 中间件

| 技术  | 版本 | 说明                                        |
| ----- | ---- | ------------------------------------------- |
| Redis | 6+   | 缓存 / 限流 / 登录安全 / 幂等 / AI 会话存储 |

---

## 🧩 功能模块

### 多角色视角

平台内置四种身份，登录后根据角色展示不同功能入口：

```
👑 系统管理员 ADMIN
   ├─ 工作台看板        全局 KPI、趋势、分类占比、最新工单
   ├─ 工单管理          全部工单的查询/分配/转派/处理
   ├─ 客户管理          用户列表、角色分配、状态管理
   ├─ 知识库            文章增删改查、审核、发布
   ├─ 智能问答          AI 问答 + 会话管理
   ├─ 数据统计          全局统计看板
   └─ 系统管理          用户角色、工单分类、审计日志

🎖️ 客服主管 SUPERVISOR
   ├─ 工作台看板        团队 KPI
   ├─ 工单管理          分配/转派/监控
   ├─ 知识库            审核、发布
   ├─ 智能问答
   ├─ 数据统计
   └─ 系统管理（部分）  用户管理、分类管理

🧑‍💼 客服人员 AGENT
   ├─ 工作台看板        我的待办
   ├─ 工单管理          接单、处理、回复、提交方案
   ├─ 知识库            检索参考
   └─ 智能问答          AI 辅助回复

👤 客户 CUSTOMER
   ├─ 工作台            我的工单概览
   ├─ 工单管理          提交工单、查看进度、确认、评价
   └─ 智能问答          自助服务
```

### 核心功能

```
🎫 工单管理
   ├─ 创建工单        标题/描述/分类/优先级
   ├─ 分配/转派        主管指定客服
   ├─ 接单/处理        客服受理、回复（公开回复/内部备注）
   ├─ 提交解决方案     客服提交 → 客户确认
   ├─ 关闭/重开        问题闭环或重新激活
   └─ 客户评价         满意度评分

📚 知识库
   ├─ 文章管理        分类、编辑、审核、发布、热门排行
   ├─ 全文检索        关键词搜索（bigram 模糊匹配）
   └─ 热门排行        按浏览量排序

🤖 智能问答
   ├─ 多轮会话        新建/切换/删除会话、加载历史
   ├─ RAG 问答        云端模型 + 知识库检索
   └─ 降级兜底        模型不可用时走本地知识检索

📊 数据统计
   ├─ 状态分布 / 优先级分布 / 满意度分布
   ├─ 客服工作量统计
   └─ 工单趋势图

⚙️ 系统管理
   ├─ 用户管理        角色分配、状态管理、密码重置
   ├─ 工单分类        增删改
   └─ 审计日志        敏感操作留痕
```

---

## 📁 项目结构

```
intelligent-service-desk/
│
├── src/main/java/com/zyyqq/desk/     # ☕ Spring Boot 后端
│   ├── entity/                       # 实体类（9 张表）
│   │   ├── User.java                 # 用户（角色/头像/状态）
│   │   ├── Ticket.java               # 工单
│   │   ├── TicketMessage.java        # 工单消息（公开回复/内部备注）
│   │   ├── TicketHistory.java        # 工单操作历史
│   │   ├── TicketCategory.java       # 工单分类
│   │   ├── KnowledgeArticle.java     # 知识文章
│   │   ├── KnowledgeCategory.java    # 知识分类
│   │   ├── Notification.java         # 通知
│   │   ├── OperationLog.java         # 审计日志
│   │   └── AiTaskLog.java            # AI 任务日志
│   ├── repository/                   # 数据访问层（Spring Data JDBC）
│   ├── service/                      # 业务逻辑层
│   │   ├── TicketService.java        # 工单状态机 + 生命周期
│   │   ├── AuthService.java          # 认证（登录/注册/头像/锁定）
│   │   ├── KnowledgeService.java     # 知识库（含 Redis 缓存）
│   │   ├── AiService.java            # AI 服务（HTTP 调用 FastAPI + 降级）
│   │   ├── StatsService.java         # 统计（含缓存）
│   │   ├── RedisSupport.java         # Redis 统一支撑（缓存/限流/幂等/登录安全）
│   │   └── ...
│   ├── controller/                   # API 控制器
│   │   ├── AuthController.java       # 认证（登录/注册/头像上传）
│   │   ├── TicketController.java     # 工单
│   │   ├── KnowledgeController.java  # 知识库
│   │   ├── AiController.java         # AI 问答（含会话代理）
│   │   ├── StatsController.java      # 统计
│   │   ├── SearchController.java     # 全局搜索
│   │   ├── NotificationController.java # 通知
│   │   └── AdminController.java      # 系统管理
│   ├── security/                     # 安全组件
│   │   ├── AuthInterceptor.java      # JWT 认证 + 角色校验
│   │   ├── RateLimitInterceptor.java # 接口限流
│   │   ├── RequireRole.java          # 角色注解
│   │   ├── AllowAnonymous.java       # 匿名放行注解
│   │   ├── JwtUtil.java              # JWT 工具
│   │   └── PasswordUtil.java         # PBKDF2 密码哈希
│   ├── common/                       # 统一响应、分页、异常
│   └── config/                       # 配置、数据初始化
│       ├── DataSeeder.java           # 演示数据初始化（幂等）
│       └── MigrationRunner.java      # 启动时自动补列
│
├── src/main/resources/
│   ├── application.yml               # 主配置（数据源/Redis/AI 服务）
│   ├── db/schema.sql                 # 建表脚本（10 张表）
│   └── static/                       # 前端构建产物
│
├── frontend/                         # 🖥️ Vue 3 前端
│   └── src/
│       ├── views/                    # 页面组件
│       │   ├── Login.vue             # 登录/注册（含身份选择）
│       │   ├── Dashboard.vue         # 工作台看板
│       │   ├── Tickets.vue           # 工单管理
│       │   ├── Customers.vue         # 客户管理
│       │   ├── Knowledge.vue         # 知识库
│       │   ├── Chat.vue              # 智能问答（含会话侧栏）
│       │   ├── Stats.vue             # 数据统计
│       │   └── System.vue            # 系统管理
│       ├── layout/MainLayout.vue     # 主布局（侧边栏 + 顶栏搜索 + 头像菜单）
│       ├── router/index.js           # 路由配置
│       ├── store/auth.js             # 登录状态（Pinia）
│       ├── api/index.js              # Axios 封装
│       └── assets/                   # 静态资源（AI 助手头像等）
│
├── ai-service/                       # 🐍 Python FastAPI AI 服务
│   ├── app/
│   │   ├── main.py                   # 主程序（5 端点 + 会话管理）
│   │   ├── llm.py                    # 云端模型调用（OpenAI 兼容）
│   │   ├── retrieval.py              # bigram 知识检索
│   │   ├── sessions.py               # 会话存储（Redis db1 优先/内存降级）
│   │   ├── schemas.py                # Pydantic 模型
│   │   └── config.py                 # 全局配置（.env 读取）
│   ├── requirements.txt              # Python 依赖
│   ├── .env.example                  # 配置模板
│   └── README.md                     # AI 服务说明
│
├── pom.xml
└── README.md
```

> 📝 **代码注释规范**：所有 Java 类（`src/main/java`）与 Python 模块（`ai-service/app`）均遵循「类头一行说明 + 每个方法前一行说明」的注释约定，便于阅读与维护。

---

## 🗄️ 数据库设计

### 数据库：`intelligent_service_desk`（MySQL 8.0，utf8mb4）

### 表结构总览

| #    | 表名                 | 说明           | 核心字段                                                     |
| ---- | -------------------- | -------------- | ------------------------------------------------------------ |
| 1    | `sys_user`           | 用户表         | id, username, password_hash, display_name, role, avatar_url, avatar_color, status |
| 2    | `ticket_category`    | 工单分类表     | id, name, sort_order                                         |
| 3    | `ticket`             | 工单表         | id, ticket_no, title, description, priority, status, customer_id, assignee_id, category_id |
| 4    | `ticket_message`     | 工单消息表     | id, ticket_id, sender_id, message_type(公开/内部), content   |
| 5    | `ticket_history`     | 工单历史表     | id, ticket_id, action, operator_id, from_status, to_status   |
| 6    | `knowledge_category` | 知识分类表     | id, name, sort_order                                         |
| 7    | `knowledge_article`  | 知识文章表     | id, title, summary, body, category_id, keywords, status, view_count, published_at |
| 8    | `notification`       | 通知表         | id, user_id, title, content, is_read                         |
| 9    | `operation_log`      | 审计日志表     | id, operator_id, action, target, detail                      |
| 10   | `ai_task_log`        | AI 任务日志表  | id, task_id, task_type, status, created_at                   |

### 工单状态机

```
待分配 PENDING → 待受理 ASSIGNED → 处理中 PROCESSING → 待客户确认 WAITING
                                                        ↓
              已关闭 CLOSED ← 已解决 RESOLVED ←（客户确认）
                  ↑____________________________|
                        （重新打开 REOPEN）
```

### 核心关系说明

- **sys_user → ticket**（客户）：一对多，一个客户提交多个工单
- **sys_user → ticket**（客服）：一对多，一个客服处理多个工单
- **ticket → ticket_message**：一对多，一个工单含多条消息（公开回复/内部备注）
- **ticket → ticket_history**：一对多，完整记录状态流转与操作轨迹
- **knowledge_category → knowledge_article**：一对多
- **sys_user → notification**：一对多

---

## 🎨 前端设计

### 布局架构

```
┌─────────────────────────────────────────────────────────────┐
│ 侧边栏     │  顶栏：页面标题 + 全局搜索(Ctrl+K) + 通知 + 头像   │
│            ├─────────────────────────────────────────────────┤
│ 工作台     │                                                 │
│ 工单管理   │                                                 │
│ 客户管理   │              主内容区（router-view）              │
│ 知识库     │                                                 │
│ 智能问答   │                                                 │
│ 数据统计   │                                                 │
│ 系统管理   │                                                 │
│            │                                                 │
│ 用户信息    │                                                 │
└────────────┴─────────────────────────────────────────────────┘
```

### 技术要点

| 方面      | 实现                                                         |
| --------- | ------------------------------------------------------------ |
| 路由      | Vue Router 4，嵌套路由 + meta 参数                           |
| 状态管理  | Pinia，存储 token 与用户信息（含角色、头像）                 |
| HTTP 请求 | Axios 封装，自动携带 JWT，统一错误处理（401 跳登录）         |
| 组件库    | Element Plus                                                 |
| 图表      | ECharts 5，看板趋势/占比、统计图表                          |
| 全局搜索  | 顶栏搜索框，Ctrl+K 快捷聚焦，防抖 300ms，检索工单/客户/知识   |
| 头像      | 上传头像存本地 `uploads/avatar/`；无头像显示昵称首字+随机底色 |
| 布局      | 主内容区居中展示（max-width 自适应），窄屏占满               |

### 登录 / 注册

- 左侧品牌展示区（Logo + 特性卡片）
- 右侧登录/注册 Tab 卡片
- 注册支持**身份选择**（客户 / 客服人员 / 客服主管，管理员仅系统内分配）

---

## 🔌 后端 API 接口

统一响应格式：`{ "code": 0, "message": "success", "data": ... }`

### 认证模块 `/api/auth`

| 方法 | 路径         | 说明                             | 认证 |
| ---- | ------------ | -------------------------------- | ---- |
| POST | `/login`     | 用户登录，返回 JWT                | ❌    |
| POST | `/register`  | 客户注册（含身份选择）            | ❌    |
| POST | `/avatar`    | 上传头像（multipart）             | ✅    |

### 工单模块 `/api/tickets`

| 方法 | 路径            | 说明                              | 认证 |
| ---- | --------------- | --------------------------------- | ---- |
| GET  | `/`             | 工单列表（按角色过滤数据范围）    | ✅    |
| POST | `/`             | 创建工单（支持 Idempotency-Key）  | ✅    |
| GET  | `/{id}`         | 工单详情（含消息、历史）          | ✅    |
| POST | `/{id}/assign`  | 分配/转派                         | ✅    |
| POST | `/{id}/accept`  | 接单                              | ✅    |
| POST | `/{id}/messages`| 回复 / 内部备注                   | ✅    |
| POST | `/{id}/resolve` | 提交解决方案                      | ✅    |
| POST | `/{id}/confirm` | 客户确认                          | ✅    |
| POST | `/{id}/close`   | 关闭                              | ✅    |
| POST | `/{id}/reopen`  | 重新打开                          | ✅    |
| POST | `/{id}/rate`    | 评价                              | ✅    |

### 知识库模块 `/api/knowledge`

| 方法 | 路径           | 说明                     | 认证 |
| ---- | -------------- | ------------------------ | ---- |
| GET  | `/`            | 文章分页列表             | ✅    |
| GET  | `/categories`  | 分类列表（Redis 缓存）   | ✅    |
| GET  | `/hot`         | 热门文章（Redis 缓存）   | ✅    |
| POST | `/`            | 新建文章                 | ✅    |
| PUT  | `/{id}`        | 编辑文章（失效缓存）     | ✅    |
| DELETE| `/{id}`       | 删除文章（失效缓存）     | ✅    |

### AI 问答模块 `/api/ai`

| 方法   | 路径           | 说明                              | 认证 |
| ------ | -------------- | --------------------------------- | ---- |
| POST   | `/chat`        | 智能问答（限流 20 次/分/用户）    | ✅    |
| POST   | `/chat/stream` | 流式智能问答（SSE，markdown 渲染）| ✅    |
| POST   | `/sessions`    | 新建会话                          | ✅    |
| GET    | `/sessions`    | 会话列表                          | ✅    |
| GET    | `/sessions/{id}` | 加载指定会话（含历史）          | ✅    |
| DELETE | `/sessions/{id}` | 删除会话                        | ✅    |

### 统计 / 搜索 / 通知

| 方法 | 路径                        | 说明                       | 认证 |
| ---- | --------------------------- | -------------------------- | ---- |
| GET  | `/api/stats/overview`       | 看板聚合统计（Redis 缓存） | ✅    |
| GET  | `/api/search?q=`            | 全局搜索（工单/客户/知识） | ✅    |
| GET  | `/api/notifications`        | 通知列表                   | ✅    |
| GET  | `/api/notifications/unread-count` | 未读通知数            | ✅    |

### 系统管理模块 `/api/admin`（🔒 ADMIN / SUPERVISOR）

| 方法   | 路径                | 说明               |
| ------ | ------------------- | ------------------ |
| GET    | `/users`            | 用户列表           |
| PUT    | `/users/{id}/status`| 修改用户状态       |
| PUT    | `/users/{id}/role`  | 分配角色           |
| PUT    | `/users/{id}/password` | 重置密码        |
| GET    | `/logs`             | 审计日志           |
| GET    | `/categories`       | 工单分类列表       |
| POST   | `/categories`       | 新增分类           |
| DELETE | `/categories/{id}`  | 删除分类           |

### AI 服务接口（FastAPI `localhost:8000`）

| 方法   | 路径                   | 说明                     |
| ------ | ---------------------- | ------------------------ |
| POST   | `/api/ai/chat`         | 智能问答（RAG，多轮）    |
| POST   | `/api/ai/summarize`    | 工单摘要                 |
| POST   | `/api/ai/classify`     | 分类推荐                 |
| POST   | `/api/ai/draft`        | 回复草稿                 |
| POST   | `/api/ai/search`       | 知识检索                 |
| POST   | `/api/ai/sessions`     | 新建会话                 |
| GET    | `/api/ai/sessions`     | 会话列表                 |
| GET    | `/api/ai/sessions/{id}`| 会话详情（含消息）       |
| DELETE | `/api/ai/sessions/{id}`| 删除会话                 |
| GET    | `/health`              | 健康检查                 |

---

## 🤖 AI 服务详解

### 架构概览

```
用户提问 → Spring Boot 后端 → FastAPI AI 服务 → 云端大模型 API → 返回结果
                │                    │
                │                    ├─ 成功：返回 AI 生成内容（RAG）
                │                    └─ 失败：降级本地知识检索（fallback:true）
                │
                └─ FastAPI 停机时：Java 直接走本地知识库检索兜底
```

### AI 服务文件结构

```
ai-service/
├── app/
│   ├── main.py              # FastAPI 主程序（5 端点 + 会话管理 + 健康检查）
│   ├── llm.py               # 云端模型调用（OpenAI 兼容，超时+错误码处理）
│   ├── retrieval.py         # bigram 知识检索（中文模糊匹配）
│   ├── sessions.py          # 会话存储（Redis db1 优先，降级内存）
│   ├── schemas.py           # Pydantic 请求/响应模型
│   └── config.py            # 全局配置（.env 读取）
├── requirements.txt
├── .env.example             # 配置模板
└── README.md
```

### 会话管理

- **数据存储**：优先 Redis（`192.168.100.128:6379` db1），Redis 不可用时自动降级进程内存
- **多轮上下文**：每次问答自动记录会话，取最近 8 条作为上下文
- **自动建会话**：首条消息无 `session_id` 时自动创建，并返回 `session_id` 供前端记忆
- **操作**：新建 / 列表 / 加载指定会话 / 删除

### 降级机制

| 场景                       | 处理                                             |
| -------------------------- | ------------------------------------------------ |
| 未配置 `AI_API_KEY`        | 走本地知识检索（bigram 匹配）                    |
| 云端模型调用失败/超时      | 降级本地知识检索，返回 `fallback:true`           |
| FastAPI 服务未启动         | Java 侧直接走本地知识检索                        |
| Redis 不可用               | 会话降级内存，缓存回源 MySQL，核心业务不受影响   |

### 配置云端模型

编辑 `ai-service/.env`（复制 `.env.example`）：

```ini
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-你的密钥
AI_MODEL=deepseek-chat
AI_TIMEOUT_SECONDS=45
AI_TEMPERATURE=0.3
```

> Java 侧通过 `app.ai-service.base-url`（默认 `http://localhost:8000`）调用 FastAPI。

---

## ⚡ Redis 缓存设计

Redis（默认 `192.168.100.128:6379` db1，可用 `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD/REDIS_DATABASE` 覆盖）承担四类临时数据，统一 Key 前缀 `isd:`：

| 前缀            | 用途                 | TTL / 策略                              |
| --------------- | -------------------- | --------------------------------------- |
| `isd:cache:*`   | 热门知识/分类/首页统计 | 60~600 秒，业务变更时主动失效           |
| `isd:auth:*`    | 登录失败计数         | 15 分钟滑窗，成功登录清除               |
| `isd:ratelimit:*`| 登录/AI 接口限流     | 60 秒固定窗口（Lua INCR+EXPIRE）        |
| `isd:idem:*`    | 工单幂等标识         | 10 分钟 SETNX                           |

### 非功能要求落地

- ✅ **过期策略**：所有缓存 Key 均设置 TTL，避免无限增长
- ✅ **统一命名**：`cache / auth / ratelimit / idem` 四类前缀区分
- ✅ **不存敏感信息**：Redis 不存明文密码、不存唯一业务记录
- ✅ **主动失效**：知识/分类/统计在业务变更时主动清除缓存
- ✅ **监控日志**：缓存命中率、耗时、Redis 异常均有日志记录
- ✅ **故障降级**：Redis 不可用时缓存回源 MySQL、限流/幂等放行并记录 WARN，核心工单业务不受影响

---

## 🚀 快速开始

### 环境要求

| 工具    | 版本要求 | 说明                         |
| ------- | -------- | ---------------------------- |
| JDK     | 21+      | 本项目使用 JDK 25            |
| Maven   | 3.9+     | 本项目使用 3.9.16            |
| MySQL   | 8.0+     | 本机 3306，root 账号         |
| Redis   | 6+       | 可选，默认 `192.168.100.128:6379` |
| Node.js | 20+      | 仅前端开发/构建时需要        |
| Python  | 3.12     | 仅 AI 服务需要               |

### 第一步：配置数据库

默认连接本机 MySQL（首次启动自动建库建表 + 初始化演示数据）：

```
host: localhost:3306
database: intelligent_service_desk
username: root
password: 123456
```

如需修改，编辑 `src/main/resources/application.yml`，或用环境变量覆盖（`DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD`）。

> 表结构（`db/schema.sql`）启动时自动执行，演示数据（1286 条工单、用户、知识文章等）由 `DataSeeder` 幂等初始化。

### 第二步：启动后端

```bash
mvn spring-boot:run
# 或打包运行
mvn package -DskipTests
java -jar target/intelligent-service-desk-0.0.1-SNAPSHOT.jar
```

后端运行在 `http://localhost:8080`（内置前端，直接访问即可）。

> ⚠️ 若环境变量存在 `PORT` 或 `SERVER_PORT`/`SERVER__PORT`（如某些 IDE/工具注入），会被 Spring Boot 的 relaxed binding 映射为 `server.port` 导致端口冲突，请显式指定端口规避：`java -jar xxx.jar --server.port=8080`

> 💡 **登录跳转提示**：登录成功后前端按后端下发的角色菜单跳转工作台；若登录后无法跳转，多为后端仍在运行旧代码（回退/改动后未重启），重启后端即可恢复。

### 第三步：前端（可选，开发模式）

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173（自动代理 /api 到 8080）
npm run build     # 构建产物输出到 src/main/resources/static
```

### 第四步：启动 AI 服务（可选）

```bash
cd ai-service
python -m venv .venv
# Windows: .venv\Scripts\activate   Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
# 配置密钥（可选，不配则走知识检索降级）
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

访问 `http://localhost:8000/health` 验证，`http://localhost:8000/docs` 查看 API 文档。

---

## 🔑 默认账号

所有账号密码均为 **123456**：

| 账号                    | 角色       | 说明                 |
| ----------------------- | ---------- | -------------------- |
| admin                   | 系统管理员 | 全部权限             |
| supervisor              | 客服主管   | 分配/转派、查看统计  |
| agent1 ~ agent8         | 客服人员   | 接单、处理工单       |
| customer1 ~ customer10  | 客户       | 提交工单、评价       |

---

## 🔒 安全说明

- 密码使用 PBKDF2（12 万次迭代）哈希存储，不明文保存
- JWT（HS256）认证，账号禁用后立即失效
- 接口级角色校验（`@RequireRole`）+ 资源级权限校验（客户只能访问自己的工单）
- 内部备注对客户接口完全隔离（后端过滤，非前端隐藏）
- 敏感操作（禁用账号、改角色、改分类）写入审计日志
- 登录失败 Redis 计数，15 分钟内失败 5 次临时锁定
- 接口限流：登录 10 次/分钟/IP、AI 问答 20 次/分钟/用户，超限返回 429
- 工单创建支持 `Idempotency-Key` 请求头防重复提交

---

## 📄 License

课程设计项目，仅供学习与演示使用。
