package com.zyyqq.desk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 轻量表结构迁移：为已有库补齐新增列，保证老库升级幂等 */
@Slf4j
@Order(0)
@Component
public class MigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public MigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 启动时执行表结构迁移 */
    @Override
    public void run(String... args) {
        // 应用级标记表（一次性数据迁移用），schema.sql 已建，这里再兜一次防止老库缺表
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_meta (" +
                "k VARCHAR(64) NOT NULL COMMENT '配置键', " +
                "v VARCHAR(255) DEFAULT NULL COMMENT '配置值', " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (k)) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '应用级标记（一次性数据迁移用）'");
        addColumnIfMissing("sys_user", "avatar_url",
                "ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像文件地址'");
        addColumnIfMissing("ticket_category", "description",
                "ALTER TABLE ticket_category ADD COLUMN description VARCHAR(200) DEFAULT NULL COMMENT '分类说明' AFTER name");
        // 操作日志：执行结果与操作人角色快照，支撑审计页的「执行结果 / 操作人角色」两列
        addColumnIfMissing("operation_log", "status",
                "ALTER TABLE operation_log ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果 SUCCESS/FAILED' AFTER detail");
        addColumnIfMissing("operation_log", "role",
                "ALTER TABLE operation_log ADD COLUMN role VARCHAR(16) DEFAULT NULL COMMENT '操作人角色快照' AFTER operator_name");
    }

    /** 列不存在时执行 DDL 补齐 */
    private void addColumnIfMissing(String table, String column, String ddl) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, column);
        if (rows.isEmpty()) {
            jdbc.execute(ddl);
            log.info("已为 {} 补充新列 {}", table, column);
        }
    }
}
