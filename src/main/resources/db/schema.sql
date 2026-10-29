-- ============================================================
-- Intelligent-Service-Desk 数据库结构
-- MySQL 8.0+ / utf8mb4
-- 幂等脚本：可重复执行，不会删除已有数据
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2 密码哈希',
    display_name  VARCHAR(64)  NOT NULL COMMENT '显示名称',
    role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN/SUPERVISOR/AGENT/CUSTOMER',
    phone         VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    company       VARCHAR(128)          DEFAULT NULL COMMENT '客户公司',
    department    VARCHAR(64)           DEFAULT NULL COMMENT '部门',
    avatar_color  VARCHAR(16)           DEFAULT NULL COMMENT '头像底色',
    avatar_url    VARCHAR(255)          DEFAULT NULL COMMENT '头像文件地址',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用',
    last_login_at DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role (role),
    KEY idx_user_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

CREATE TABLE IF NOT EXISTS ticket_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL COMMENT '分类名称',
    description VARCHAR(200)         DEFAULT NULL COMMENT '分类说明',
    color      VARCHAR(16)          DEFAULT NULL COMMENT '图表颜色',
    sla_hours  INT                  DEFAULT 24 COMMENT '解决时限（小时）',
    sort       INT         NOT NULL DEFAULT 0,
    status     TINYINT     NOT NULL DEFAULT 1,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单分类';

CREATE TABLE IF NOT EXISTS ticket (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_no             VARCHAR(32)  NOT NULL COMMENT '工单编号',
    title                 VARCHAR(200) NOT NULL COMMENT '工单主题',
    description           TEXT COMMENT '问题描述',
    customer_id           BIGINT       NOT NULL COMMENT '创建客户',
    assignee_id           BIGINT                DEFAULT NULL COMMENT '当前处理人',
    category_id           BIGINT                DEFAULT NULL COMMENT '工单分类',
    priority              VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'URGENT/HIGH/MEDIUM/LOW',
    status                VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ASSIGNED/PROCESSING/WAITING/RESOLVED/CLOSED',
    source                VARCHAR(24)  NOT NULL DEFAULT 'WEB' COMMENT '来源渠道',
    solution              TEXT COMMENT '解决方案',
    satisfaction          TINYINT               DEFAULT NULL COMMENT '满意度 1-5',
    feedback              VARCHAR(500)          DEFAULT NULL COMMENT '客户评价内容',
    sla_response_at       DATETIME              DEFAULT NULL COMMENT '首次响应目标时间',
    sla_resolve_at        DATETIME              DEFAULT NULL COMMENT '解决目标时间',
    first_response_at     DATETIME              DEFAULT NULL COMMENT '首次响应时间',
    resolved_at           DATETIME              DEFAULT NULL COMMENT '首次解决时间',
    closed_at             DATETIME              DEFAULT NULL COMMENT '关闭时间',
    reopen_count          INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_ticket_status (status),
    KEY idx_ticket_customer (customer_id),
    KEY idx_ticket_assignee (assignee_id),
    KEY idx_ticket_created (created_at),
    KEY idx_ticket_category (category_id),
    KEY idx_ticket_priority (priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单主表';

CREATE TABLE IF NOT EXISTS ticket_message (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id    BIGINT      NOT NULL,
    sender_id    BIGINT      NOT NULL,
    message_type VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC 客户可见 / INTERNAL 内部备注',
    content      TEXT        NOT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_msg_ticket (ticket_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单消息';

CREATE TABLE IF NOT EXISTS ticket_history (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT      NOT NULL,
    operator_id BIGINT               DEFAULT NULL,
    action      VARCHAR(64) NOT NULL COMMENT '操作类型',
    old_value   VARCHAR(255)         DEFAULT NULL,
    new_value   VARCHAR(255)         DEFAULT NULL,
    remark      VARCHAR(500)         DEFAULT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_history_ticket (ticket_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单操作历史';

CREATE TABLE IF NOT EXISTS knowledge_category (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    name   VARCHAR(64) NOT NULL,
    sort   INT         NOT NULL DEFAULT 0,
    status TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kcat_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识分类';

CREATE TABLE IF NOT EXISTS knowledge_article (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL,
    summary      VARCHAR(500)          DEFAULT NULL,
    body         MEDIUMTEXT COMMENT '正文（Markdown）',
    category_id  BIGINT                DEFAULT NULL,
    keywords     VARCHAR(255)          DEFAULT NULL COMMENT '逗号分隔关键词',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEW/PUBLISHED/OFFLINE',
    author_id    BIGINT                DEFAULT NULL,
    view_count   INT          NOT NULL DEFAULT 0,
    published_at DATETIME              DEFAULT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_article_status (status),
    KEY idx_article_category (category_id),
    KEY idx_article_published (published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识文章';

CREATE TABLE IF NOT EXISTS knowledge_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    article_id BIGINT   NOT NULL COMMENT '文章 ID',
    user_id    BIGINT   NOT NULL COMMENT '收藏人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fav_article_user (article_id, user_id),
    KEY idx_fav_user (user_id),
    KEY idx_fav_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识文章收藏';

CREATE TABLE IF NOT EXISTS notification (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL DEFAULT 'TICKET',
    title        VARCHAR(200) NOT NULL,
    content      VARCHAR(500)          DEFAULT NULL,
    ticket_id    BIGINT                DEFAULT NULL,
    is_read      TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notify_recipient (recipient_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '站内通知';

CREATE TABLE IF NOT EXISTS operation_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id   BIGINT                DEFAULT NULL,
    operator_name VARCHAR(64)           DEFAULT NULL,
    role          VARCHAR(16)           DEFAULT NULL COMMENT '操作人角色快照',
    action        VARCHAR(64)  NOT NULL,
    target_type   VARCHAR(32)           DEFAULT NULL,
    target_id     VARCHAR(64)           DEFAULT NULL,
    detail        VARCHAR(1000)         DEFAULT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果 SUCCESS/FAILED',
    ip            VARCHAR(64)           DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_log_created (created_at),
    KEY idx_log_operator (operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '操作审计日志';

CREATE TABLE IF NOT EXISTS ai_task_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    request_id    VARCHAR(64) NOT NULL,
    task_type     VARCHAR(32) NOT NULL COMMENT 'QA/SUMMARIZE/CLASSIFY/DRAFT/STEPS',
    user_id       BIGINT               DEFAULT NULL COMMENT '发起用户 ID',
    user_name     VARCHAR(64)          DEFAULT NULL COMMENT '发起用户姓名快照',
    user_role     VARCHAR(32)          DEFAULT NULL COMMENT '发起用户角色快照',
    ticket_no     VARCHAR(32)          DEFAULT NULL COMMENT '关联工单号',
    status        VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED/FALLBACK',
    duration_ms   INT                  DEFAULT NULL,
    question      VARCHAR(500)         DEFAULT NULL COMMENT '提示词或问题',
    answer        TEXT                 DEFAULT NULL COMMENT 'AI 回答正文（超长截断）',
    answer_length INT                  DEFAULT NULL,
    model         VARCHAR(64)          DEFAULT NULL,
    source        VARCHAR(200)         DEFAULT NULL COMMENT '引用知识',
    error_summary VARCHAR(500)         DEFAULT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_created (created_at),
    KEY idx_ai_type (task_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 调用日志';

CREATE TABLE IF NOT EXISTS app_meta (
    k          VARCHAR(64)  NOT NULL COMMENT '配置键',
    v          VARCHAR(255)          DEFAULT NULL COMMENT '配置值',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (k)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '应用级标记（一次性数据迁移用）';
