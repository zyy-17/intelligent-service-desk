package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 团队工作台服务：客服负载、超时与临期预警、待分配工单与批量指派 */
@Service
public class TeamService {

    /** 算作「在办」的工单状态，超时判断只针对这些状态 */
    private static final String ACTIVE_STATUS = "('ASSIGNED','PROCESSING','WAITING','PENDING_REVIEW')";

    /** 距解决时限不足该小时数视为「临期」 */
    private static final int DUE_SOON_HOURS = 4;

    private final JdbcTemplate jdbc;
    private final TicketService ticketService;

    public TeamService(JdbcTemplate jdbc, TicketService ticketService) {
        this.jdbc = jdbc;
        this.ticketService = ticketService;
    }

    /** 工作台概览：顶部指标 + 客服负载列表 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats());
        data.put("agents", agentLoad());
        data.put("dueSoonHours", DUE_SOON_HOURS);
        return data;
    }

    /** 顶部指标：待分配、超时、临期、待审核、今日新增与今日解决 */
    private Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unassigned", count("SELECT COUNT(*) FROM ticket WHERE status = 'PENDING'"));
        m.put("overdue", count("SELECT COUNT(*) FROM ticket WHERE status IN " + ACTIVE_STATUS +
                " AND sla_resolve_at IS NOT NULL AND sla_resolve_at < NOW()"));
        m.put("dueSoon", count("SELECT COUNT(*) FROM ticket WHERE status IN " + ACTIVE_STATUS +
                " AND sla_resolve_at IS NOT NULL AND sla_resolve_at >= NOW()" +
                " AND sla_resolve_at <= DATE_ADD(NOW(), INTERVAL " + DUE_SOON_HOURS + " HOUR)"));
        m.put("pendingReview", count("SELECT COUNT(*) FROM ticket WHERE status = 'PENDING_REVIEW'"));
        m.put("todayCreated", count("SELECT COUNT(*) FROM ticket WHERE created_at >= CURDATE()"));
        m.put("todayResolved", count("SELECT COUNT(*) FROM ticket WHERE resolved_at >= CURDATE()"));
        m.put("agentCount", count("SELECT COUNT(*) FROM sys_user WHERE role = 'AGENT' AND status = 1"));
        return m;
    }

    /** 客服负载：在办工单数、超时数、今日新增、今日解决、近 30 天评分 */
    private List<Map<String, Object>> agentLoad() {
        return jdbc.queryForList(
                "SELECT u.id, u.display_name AS name, u.department, u.avatar_color, " +
                        "  SUM(CASE WHEN t.status IN " + ACTIVE_STATUS + " THEN 1 ELSE 0 END) AS active, " +
                        "  SUM(CASE WHEN t.status IN " + ACTIVE_STATUS +
                        "           AND t.sla_resolve_at IS NOT NULL AND t.sla_resolve_at < NOW() THEN 1 ELSE 0 END) AS overdue, " +
                        "  SUM(CASE WHEN t.assignee_id IS NOT NULL AND t.created_at >= CURDATE() THEN 1 ELSE 0 END) AS assigned_today, " +
                        "  SUM(CASE WHEN t.resolved_at >= CURDATE() THEN 1 ELSE 0 END) AS resolved_today, " +
                        "  SUM(CASE WHEN t.satisfaction IS NOT NULL THEN 1 ELSE 0 END) AS rated, " +
                        "  ROUND(AVG(CASE WHEN t.satisfaction IS NOT NULL THEN t.satisfaction END), 1) AS avg_rating " +
                        "FROM sys_user u LEFT JOIN ticket t ON t.assignee_id = u.id " +
                        "WHERE u.role = 'AGENT' AND u.status = 1 " +
                        "GROUP BY u.id, u.display_name, u.department, u.avatar_color " +
                        "ORDER BY active DESC, u.id");
    }

    /**
     * 工作台工单列表。
     *
     * @param type unassigned 待分配 / overdue 已超时 / dueSoon 即将超时
     */
    public List<Map<String, Object>> tickets(String type) {
        String select = "SELECT t.id, t.ticket_no, t.title, t.status, t.priority, t.source, " +
                "  t.created_at, t.sla_response_at, t.sla_resolve_at, " +
                "  c.display_name AS customer_name, a.display_name AS assignee_name, a.avatar_color AS assignee_color, " +
                "  cat.name AS category_name, cat.color AS category_color, " +
                "  TIMESTAMPDIFF(MINUTE, NOW(), t.sla_resolve_at) AS remain_minutes " +
                "FROM ticket t " +
                "LEFT JOIN sys_user c ON c.id = t.customer_id " +
                "LEFT JOIN sys_user a ON a.id = t.assignee_id " +
                "LEFT JOIN ticket_category cat ON cat.id = t.category_id ";
        String order = " ORDER BY FIELD(t.priority,'URGENT','HIGH','MEDIUM','LOW'), t.created_at ";

        if ("overdue".equalsIgnoreCase(type)) {
            return jdbc.queryForList(select + "WHERE t.status IN " + ACTIVE_STATUS +
                    " AND t.sla_resolve_at IS NOT NULL AND t.sla_resolve_at < NOW()" +
                    " ORDER BY t.sla_resolve_at LIMIT 100");
        }
        if ("dueSoon".equalsIgnoreCase(type)) {
            return jdbc.queryForList(select + "WHERE t.status IN " + ACTIVE_STATUS +
                    " AND t.sla_resolve_at IS NOT NULL AND t.sla_resolve_at >= NOW()" +
                    " AND t.sla_resolve_at <= DATE_ADD(NOW(), INTERVAL " + DUE_SOON_HOURS + " HOUR)" +
                    " ORDER BY t.sla_resolve_at LIMIT 100");
        }
        return jdbc.queryForList(select + "WHERE t.status = 'PENDING' AND t.assignee_id IS NULL" + order + "LIMIT 100");
    }

    /** 批量指派：逐单复用工单服务的分配逻辑，单条失败不阻断其余工单 */
    public Map<String, Object> assign(List<Long> ticketIds, Long assigneeId) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            throw new BizException("请先选择工单");
        }
        if (assigneeId == null) {
            throw new BizException("请选择处理人");
        }
        List<String> targets = jdbc.queryForList(
                "SELECT role FROM sys_user WHERE id = ? AND status = 1", String.class, assigneeId);
        if (targets.isEmpty() || !"AGENT".equals(targets.get(0))) {
            throw new BizException("只能指派给启用状态的客服");
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ticketIds) {
            try {
                ticketService.assign(id, assigneeId);
                ok++;
            } catch (Exception e) {
                errors.add("工单 " + id + "：" + (e.getMessage() == null ? "指派失败" : e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assigned", ok);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    /** 单值计数，null 视为 0 */
    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}
