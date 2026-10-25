package com.zyyqq.desk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统计数据访问类：趋势、分布、均值、超时等聚合查询 */
@Repository
public class StatsRepository {

    private final JdbcTemplate jdbc;

    public StatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 近 N 天工单趋势（含创建与解决） */
    public List<Map<String, Object>> trend(int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.format(fmt);
            Long created = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ticket WHERE DATE(created_at) = ?", Long.class, d.toString());
            Long resolved = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ticket WHERE DATE(resolved_at) = ?", Long.class, d.toString());
            Map<String, Object> row = new HashMap<>();
            row.put("date", key);
            row.put("created", created);
            row.put("resolved", resolved);
            result.add(row);
        }
        return result;
    }

    /** 分类分布 */
    public List<Map<String, Object>> categoryDistribution() {
        return jdbc.queryForList(
                "SELECT COALESCE(c.name,'未分类') AS name, COALESCE(c.color,'#909399') AS color, COUNT(t.id) AS value " +
                        "FROM ticket t LEFT JOIN ticket_category c ON c.id = t.category_id " +
                        "GROUP BY c.name, c.color ORDER BY value DESC");
    }

    /** 状态分布 */
    public List<Map<String, Object>> statusDistribution() {
        return jdbc.queryForList(
                "SELECT status AS name, COUNT(*) AS value FROM ticket GROUP BY status ORDER BY value DESC");
    }

    /** 优先级分布 */
    public List<Map<String, Object>> priorityDistribution() {
        return jdbc.queryForList(
                "SELECT priority AS name, COUNT(*) AS value FROM ticket GROUP BY priority");
    }

    /** 客服工作量（处理中+已解决+已关闭的工单数，按处理人） */
    public List<Map<String, Object>> agentWorkload() {
        return jdbc.queryForList(
                "SELECT u.display_name AS name, COUNT(t.id) AS value " +
                        "FROM ticket t JOIN sys_user u ON u.id = t.assignee_id " +
                        "WHERE u.role = 'AGENT' GROUP BY u.display_name ORDER BY value DESC");
    }

    /** 满意度分布 */
    public List<Map<String, Object>> satisfaction() {
        return jdbc.queryForList(
                "SELECT satisfaction AS rating, COUNT(*) AS value FROM ticket WHERE satisfaction IS NOT NULL " +
                        "GROUP BY satisfaction ORDER BY satisfaction");
    }

    /** 近 N 天满意度均值 */
    public Double avgSatisfaction(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        Double v = jdbc.queryForObject(
                "SELECT AVG(satisfaction) FROM ticket WHERE satisfaction IS NOT NULL AND resolved_at >= ?",
                Double.class, from.atStartOfDay());
        return v == null ? 0 : Math.round(v * 10.0) / 10.0;
    }

    /** 平均首次响应时长（分钟） */
    public Double avgFirstResponseMinutes(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        Double v = jdbc.queryForObject(
                "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, first_response_at)) FROM ticket " +
                        "WHERE first_response_at IS NOT NULL AND created_at >= ?",
                Double.class, from.atStartOfDay());
        return v == null ? 0.0 : (double) Math.round(v);
    }

    /** 平均解决时长（小时） */
    public Double avgResolveHours(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        Double v = jdbc.queryForObject(
                "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)) / 60 FROM ticket " +
                        "WHERE resolved_at IS NOT NULL AND created_at >= ?",
                Double.class, from.atStartOfDay());
        return v == null ? 0 : Math.round(v * 10.0) / 10.0;
    }

    /** 近 N 天新增工单数 */
    public long countNew(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE created_at >= ?", Long.class, from.atStartOfDay());
    }

    /** 超时工单数：非终态且已超过解决目标时间 */
    public long countOverdueInternal() {
        Long v = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE status NOT IN ('RESOLVED','CLOSED') " +
                        "AND sla_resolve_at IS NOT NULL AND sla_resolve_at < NOW()", Long.class);
        return v == null ? 0 : v;
    }
}
