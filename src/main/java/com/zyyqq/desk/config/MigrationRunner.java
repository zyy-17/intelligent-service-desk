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
        addColumnIfMissing("sys_user", "avatar_url",
                "ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像文件地址'");
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
