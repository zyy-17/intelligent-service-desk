package com.zyyqq.desk.service;

import com.zyyqq.desk.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户评价服务：评价概览统计与明细查询，供管理员/客服主管查看 */
@Service
public class EvaluationService {

    private final JdbcTemplate jdbc;

    public EvaluationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 评价概览：数量、平均分、好评率、评价覆盖率、星级分布、客服排行与近 30 天趋势 */
    public Map<String, Object> stats() {
        long total = count("SELECT COUNT(*) FROM ticket WHERE satisfaction IS NOT NULL");
        long resolved = count("SELECT COUNT(*) FROM ticket WHERE status IN ('RESOLVED','CLOSED')");
        Double avg = jdbc.queryForObject(
                "SELECT ROUND(AVG(satisfaction), 1) FROM ticket WHERE satisfaction IS NOT NULL", Double.class);
        long good = count("SELECT COUNT(*) FROM ticket WHERE satisfaction >= 4");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("resolvedTotal", resolved);
        m.put("avgRating", avg == null ? 0 : avg);
        m.put("goodRate", total == 0 ? 0 : Math.round(good * 1000.0 / total) / 10.0);
        m.put("coverRate", resolved == 0 ? 0 : Math.round(total * 1000.0 / resolved) / 10.0);
        m.put("distribution", distribution());
        m.put("agents", agentRanking());
        m.put("trend", trend(30));
        return m;
    }

    /** 各星级评价数量（1~5 星，缺档补 0，方便前端直接画图） */
    private List<Map<String, Object>> distribution() {
        Map<Integer, Long> counted = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT satisfaction AS rating, COUNT(*) AS cnt FROM ticket " +
                        "WHERE satisfaction IS NOT NULL GROUP BY satisfaction")) {
            counted.put(((Number) row.get("rating")).intValue(), ((Number) row.get("cnt")).longValue());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rating", star);
            item.put("value", counted.getOrDefault(star, 0L));
            list.add(item);
        }
        return list;
    }

    /** 客服维度排行：每个人收到的评价数、平均分、好评率，按平均分与评价数排序 */
    private List<Map<String, Object>> agentRanking() {
        return jdbc.queryForList(
                "SELECT a.id AS agent_id, a.display_name AS name, a.avatar_color, " +
                        "       COUNT(t.id) AS rated, " +
                        "       ROUND(AVG(t.satisfaction), 1) AS avg_rating, " +
                        "       ROUND(SUM(CASE WHEN t.satisfaction >= 4 THEN 1 ELSE 0 END) * 100.0 / COUNT(t.id), 1) AS good_rate, " +
                        "       SUM(CASE WHEN t.satisfaction = 5 THEN 1 ELSE 0 END) AS five_count " +
                        "FROM ticket t JOIN sys_user a ON a.id = t.assignee_id " +
                        "WHERE t.satisfaction IS NOT NULL " +
                        "GROUP BY a.id, a.display_name, a.avatar_color " +
                        "ORDER BY avg_rating DESC, rated DESC");
    }

    /** 近 N 天评价趋势（按评价发生时间统计，时间取自工单历史里的 RATE 记录） */
    private List<Map<String, Object>> trend(int days) {
        return jdbc.queryForList(
                "SELECT DATE(r.rated_at) AS day, COUNT(*) AS cnt, ROUND(AVG(t.satisfaction), 1) AS avg_rating " +
                        "FROM ticket t JOIN (SELECT ticket_id, MAX(created_at) AS rated_at FROM ticket_history " +
                        "                   WHERE action = 'RATE' GROUP BY ticket_id) r ON r.ticket_id = t.id " +
                        "WHERE r.rated_at >= ? GROUP BY DATE(r.rated_at) ORDER BY day",
                LocalDate.now().minusDays(days).atStartOfDay());
    }

    /**
     * 评价明细分页：工单信息 + 客户 + 处理客服 + 星级 + 评语 + 评价时间。
     * 联表字段用 JdbcTemplate 返回 Map，避免实体映射丢字段。
     *
     * @param rating  星级筛选（1~5），为空不限
     * @param agentId 客服筛选，为空不限
     * @param keyword 工单号 / 主题 / 客户名 模糊匹配
     * @param start   评价时间起（yyyy-MM-dd）
     * @param end     评价时间止（yyyy-MM-dd）
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize, Integer rating, Long agentId,
                                                String keyword, String start, String end) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" WHERE t.satisfaction IS NOT NULL ");
        List<Object> args = new ArrayList<>();
        if (rating != null && rating >= 1 && rating <= 5) {
            where.append(" AND t.satisfaction = ? ");
            args.add(rating);
        }
        if (agentId != null) {
            where.append(" AND t.assignee_id = ? ");
            args.add(agentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.ticket_no LIKE ? OR t.title LIKE ? OR c.display_name LIKE ? OR a.display_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (start != null && !start.isBlank()) {
            where.append(" AND COALESCE(r.rated_at, t.resolved_at, t.created_at) >= ? ");
            args.add(LocalDate.parse(start).atStartOfDay());
        }
        if (end != null && !end.isBlank()) {
            where.append(" AND COALESCE(r.rated_at, t.resolved_at, t.created_at) <= ? ");
            args.add(LocalDate.parse(end).atTime(23, 59, 59));
        }

        String from = " FROM ticket t " +
                "LEFT JOIN sys_user c ON c.id = t.customer_id " +
                "LEFT JOIN sys_user a ON a.id = t.assignee_id " +
                "LEFT JOIN (SELECT ticket_id, MAX(created_at) AS rated_at FROM ticket_history " +
                "           WHERE action = 'RATE' GROUP BY ticket_id) r ON r.ticket_id = t.id ";
        long total = count("SELECT COUNT(*)" + from + where, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((p - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT t.id AS ticket_id, t.ticket_no, t.title, t.status, t.satisfaction AS rating, " +
                        "t.feedback AS comment, t.resolved_at, t.closed_at, " +
                        "c.display_name AS customer_name, c.avatar_color AS customer_avatar_color, " +
                        "a.id AS agent_id, a.display_name AS agent_name, a.avatar_color AS agent_avatar_color, " +
                        "COALESCE(r.rated_at, t.resolved_at, t.created_at) AS rated_at" +
                        from + where + " ORDER BY rated_at DESC, t.id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(list, total, p, size);
    }

    /** 参与过处理的客服列表（评价筛选下拉用） */
    public List<Map<String, Object>> agents() {
        return jdbc.queryForList(
                "SELECT id, display_name AS name FROM sys_user WHERE role = 'AGENT' ORDER BY id");
    }

    /** 单值计数，null 视为 0 */
    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
