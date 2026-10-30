package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户评价服务：评价概览统计、明细查询与低分跟进闭环，供管理员/客服主管查看 */
@Service
public class EvaluationService {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public EvaluationService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
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
        // 低分跟进：1-2 星需要回访，按状态给出待办数量
        m.put("lowTotal", count("SELECT COUNT(*) FROM ticket WHERE satisfaction IS NOT NULL AND satisfaction <= 2"));
        m.put("pendingFollow", count("SELECT COUNT(*) FROM ticket WHERE satisfaction <= 2 " +
                "AND COALESCE(follow_status, 'PENDING') = 'PENDING'"));
        m.put("doingFollow", count("SELECT COUNT(*) FROM ticket WHERE satisfaction <= 2 AND follow_status = 'DOING'"));
        m.put("doneFollow", count("SELECT COUNT(*) FROM ticket WHERE satisfaction <= 2 AND follow_status = 'DONE'"));
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
     * @param followStatus 低分跟进状态（PENDING/DOING/DONE），为空不限
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize, Integer rating, Long agentId,
                                                String keyword, String start, String end, String followStatus) {
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
        if (followStatus != null && !followStatus.isBlank()) {
            // 未跟进过的低分评价按「待跟进」处理，与页面展示保持一致
            where.append(" AND t.satisfaction <= 2 AND COALESCE(t.follow_status, 'PENDING') = ? ");
            args.add(followStatus.trim().toUpperCase());
        }

        String from = " FROM ticket t " +
                "LEFT JOIN sys_user c ON c.id = t.customer_id " +
                "LEFT JOIN sys_user a ON a.id = t.assignee_id " +
                "LEFT JOIN sys_user f ON f.id = t.follow_by " +
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
                        "CASE WHEN t.satisfaction <= 2 THEN COALESCE(t.follow_status, 'PENDING') END AS follow_status, " +
                        "t.follow_note, t.follow_at, f.display_name AS follow_by_name, " +
                        "COALESCE(r.rated_at, t.resolved_at, t.created_at) AS rated_at" +
                        from + where + " ORDER BY rated_at DESC, t.id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(list, total, p, size);
    }

    /**
     * 低分评价跟进：更新状态、备注、处理人与时间。
     * 仅 1-2 星的低分评价需要跟进，避免把这块做成对全部评价的泛化操作。
     *
     * @param status PENDING 待跟进 / DOING 跟进中 / DONE 已闭环
     */
    public void follow(Long ticketId, String status, String note) {
        AuthContext.UserInfo u = AuthContext.require();
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PENDING", "DOING", "DONE").contains(s)) {
            throw new BizException("跟进状态不合法");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ticket_no, satisfaction FROM ticket WHERE id = ?", ticketId);
        if (rows.isEmpty()) {
            throw BizException.notFound("工单不存在");
        }
        Object sat = rows.get(0).get("satisfaction");
        if (sat == null || ((Number) sat).intValue() > 2) {
            throw new BizException("只有 1-2 星的低分评价需要跟进");
        }
        String text = note == null ? null : (note.length() > 500 ? note.substring(0, 500) : note);
        jdbc.update("UPDATE ticket SET follow_status = ?, follow_note = ?, follow_by = ?, follow_at = ? WHERE id = ?",
                s, text, u.id(), LocalDateTime.now(), ticketId);
        String label = "PENDING".equals(s) ? "标记待跟进" : ("DOING".equals(s) ? "标记跟进中" : "标记已闭环");
        auditService.ok("FOLLOW_EVALUATION", "TICKET", String.valueOf(rows.get(0).get("ticket_no")),
                "低分评价" + label + (text == null || text.isBlank() ? "" : "：" + text));
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
