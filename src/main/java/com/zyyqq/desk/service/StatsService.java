package com.zyyqq.desk.service;

import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.repository.StatsRepository;
import com.zyyqq.desk.repository.TicketRepository;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统计服务：全站概况、工单趋势、团队绩效、按角色的个人概览 */
@Service
public class StatsService {

    private final StatsRepository statsRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RedisSupport redisSupport;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public StatsService(StatsRepository statsRepository, TicketRepository ticketRepository,
                        UserRepository userRepository, RedisSupport redisSupport, AppProperties props,
                        JdbcTemplate jdbc) {
        this.statsRepository = statsRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.redisSupport = redisSupport;
        this.props = props;
        this.jdbc = jdbc;
    }

    /** 全站服务概况（带 Redis 热点缓存） */
    public Map<String, Object> overview() {
        // 首页统计热点缓存：短 TTL，Redis 不可用时直接回源 MySQL
        return redisSupport.cacheJson("stats_overview", Map.class,
                Duration.ofSeconds(props.getRedis().getStatsOverviewTtl()),
                this::loadOverview);
    }

    /** 从数据库加载全站概况数据 */
    private Map<String, Object> loadOverview() {
        Map<String, Object> data = new HashMap<>();
        long total = ticketRepository.countAll();
        long pending = ticketRepository.countByStatus("PENDING") + ticketRepository.countByStatus("ASSIGNED");
        long processing = ticketRepository.countByStatus("PROCESSING");
        long resolved = ticketRepository.countByStatus("RESOLVED") + ticketRepository.countByStatus("CLOSED");

        // 超时：当前时间超过解决目标时间且未到终态
        long overdue = countOverdue();

        data.put("totalTickets", total);
        data.put("pendingTickets", pending);
        data.put("processingTickets", processing);
        data.put("resolvedTickets", resolved);
        data.put("overdueTickets", overdue);
        data.put("todayNew", statsRepository.countNew(1));
        data.put("avgResponseMinutes", statsRepository.avgFirstResponseMinutes(7));
        data.put("avgResolveHours", statsRepository.avgResolveHours(7));
        data.put("satisfaction", statsRepository.avgSatisfaction(30));
        data.put("customerCount", userRepository.countByRole("CUSTOMER"));
        data.put("agentCount", userRepository.countByRole("AGENT"));

        data.put("trend", statsRepository.trend(7));
        data.put("categoryDistribution", statsRepository.categoryDistribution());
        data.put("statusDistribution", statsRepository.statusDistribution());
        data.put("priorityDistribution", statsRepository.priorityDistribution());
        data.put("agentWorkload", statsRepository.agentWorkload());
        data.put("satisfactionDistribution", statsRepository.satisfaction());
        return data;
    }

    /** 近 N 天工单趋势 */
    public List<Map<String, Object>> trend(int days) {
        return statsRepository.trend(Math.min(Math.max(days, 1), 90));
    }

    /** 团队绩效看板：每位客服的工单量、解决数、满意度、超时数等 */
    public List<Map<String, Object>> teamPerformance() {
        return jdbc.queryForList(
                "SELECT u.id AS agent_id, u.display_name AS name, " +
                "       COUNT(t.id) AS total, " +
                "       SUM(CASE WHEN t.status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved, " +
                "       SUM(CASE WHEN t.status NOT IN ('RESOLVED','CLOSED') AND t.sla_resolve_at IS NOT NULL " +
                "                AND t.sla_resolve_at < NOW() THEN 1 ELSE 0 END) AS overdue, " +
                "       ROUND(AVG(t.satisfaction), 1) AS satisfaction, " +
                "       ROUND(AVG(CASE WHEN t.first_response_at IS NOT NULL " +
                "                THEN TIMESTAMPDIFF(MINUTE, t.created_at, t.first_response_at) END)) AS avg_response_min, " +
                "       ROUND(AVG(CASE WHEN t.resolved_at IS NOT NULL " +
                "                THEN TIMESTAMPDIFF(MINUTE, t.created_at, t.resolved_at) / 60 END), 1) AS avg_resolve_hours " +
                "FROM sys_user u LEFT JOIN ticket t ON t.assignee_id = u.id " +
                "WHERE u.role = 'AGENT' " +
                "GROUP BY u.id, u.display_name " +
                "ORDER BY total DESC");
    }

    /** 我的概览：按角色返回差异化首页数据（客户/客服/主管管理员） */
    public Map<String, Object> myOverview() {
        AuthContext.UserInfo u = AuthContext.require();
        if ("CUSTOMER".equals(u.role())) {
            return myCustomerOverview(u.id());
        }
        if ("AGENT".equals(u.role())) {
            return myAgentOverview(u.id());
        }
        // 主管/管理员看全站
        return overview();
    }

    /** 客户个人概览：我提交的工单分状态统计与最新工单 */
    private Map<String, Object> myCustomerOverview(long uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("role", "CUSTOMER");
        data.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE customer_id = ?", Long.class, uid));
        data.put("processing", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status IN ('PENDING','ASSIGNED','PROCESSING')", Long.class, uid));
        data.put("waiting", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status = 'WAITING'", Long.class, uid));
        data.put("resolved", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status IN ('RESOLVED','CLOSED')", Long.class, uid));
        // 最新工单
        data.put("recent", jdbc.queryForList(
                "SELECT id, ticket_no, title, priority, status, created_at FROM ticket " +
                "WHERE customer_id = ? ORDER BY created_at DESC LIMIT 6", uid));
        return data;
    }

    /** 客服个人概览：分配给我的工单统计与待办列表 */
    private Map<String, Object> myAgentOverview(long uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("role", "AGENT");
        data.put("assigned", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status = 'ASSIGNED'", Long.class, uid));
        data.put("processing", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status = 'PROCESSING'", Long.class, uid));
        data.put("waiting", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status = 'WAITING'", Long.class, uid));
        data.put("resolved", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status IN ('RESOLVED','CLOSED')", Long.class, uid));
        // 我的待办工单
        data.put("todo", jdbc.queryForList(
                "SELECT t.id, t.ticket_no, t.title, t.priority, t.status, t.created_at, cu.display_name AS customer_name " +
                "FROM ticket t LEFT JOIN sys_user cu ON cu.id = t.customer_id " +
                "WHERE t.assignee_id = ? AND t.status IN ('ASSIGNED','PROCESSING','WAITING') " +
                "ORDER BY t.created_at DESC LIMIT 6", uid));
        return data;
    }

    /** 统计超时工单数（非终态且超过解决目标时间） */
    private long countOverdue() {
        // 终态：RESOLVED/CLOSED；非终态且 sla_resolve_at 已过为超时
        return statsRepository.countOverdueInternal();
    }
}
