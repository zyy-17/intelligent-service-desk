package com.zyyqq.desk.service;

import com.zyyqq.desk.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 上下文装配：读取用户身份、近期工单与知识库目录，供模型做个性化回答 */
@Slf4j
@Service
public class AiContextService {

    /** 知识库目录最多下发的文章数（避免上下文过长） */
    private static final int CATALOG_LIMIT = 60;
    /** 近期工单最多下发的条数 */
    private static final int TICKET_LIMIT = 5;
    /** 工单解决方案截断长度 */
    private static final int SOLUTION_MAX = 200;

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public AiContextService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    /** 读取用户画像：身份、联系方式、工单概况（客户看自己提交的，客服看自己处理的） */
    public Map<String, Object> profile(long uid, String role) {
        String scope = ticketScope(uid, role);
        try {
            return jdbc.queryForObject("""
                    SELECT u.id, u.username, u.display_name, u.role, u.company, u.department,
                           u.email, u.phone, DATE_FORMAT(u.created_at, '%%Y-%%m-%%d') AS joined_at,
                           (SELECT COUNT(*) FROM ticket t WHERE %s) AS ticket_total,
                           (SELECT COUNT(*) FROM ticket t WHERE %s AND t.status IN ('PENDING','ASSIGNED','PROCESSING','WAITING')) AS ticket_processing,
                           (SELECT COUNT(*) FROM ticket t WHERE %s AND t.status IN ('RESOLVED','CLOSED')) AS ticket_resolved
                    FROM sys_user u WHERE u.id = ?
                    """.formatted(scope, scope, scope),
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", rs.getLong("id"));
                        m.put("username", rs.getString("username"));
                        m.put("display_name", rs.getString("display_name"));
                        m.put("role", rs.getString("role"));
                        m.put("company", rs.getString("company"));
                        m.put("department", rs.getString("department"));
                        m.put("email", rs.getString("email"));
                        m.put("phone", rs.getString("phone"));
                        m.put("joined_at", rs.getString("joined_at"));
                        m.put("ticket_total", rs.getInt("ticket_total"));
                        m.put("ticket_processing", rs.getInt("ticket_processing"));
                        m.put("ticket_resolved", rs.getInt("ticket_resolved"));
                        return m;
                    }, uid);
        } catch (Exception e) {
            log.warn("读取 AI 用户画像失败 uid={}：{}", uid, e.getMessage());
            return Map.of();
        }
    }

    /** 读取近期工单：客户看自己提交的，客服看自己处理的，管理员看全站最新 */
    public List<Map<String, Object>> recentTickets(long uid, String role) {
        String where = switch (role == null ? "" : role) {
            case "CUSTOMER" -> "t.customer_id = ?";
            case "AGENT" -> "t.assignee_id = ?";
            default -> "1 = 1";
        };
        String sql = """
                SELECT t.ticket_no, t.title, t.status, t.priority,
                       DATE_FORMAT(t.created_at, '%%Y-%%m-%%d') AS created_day,
                       c.name AS category_name, t.solution
                FROM ticket t
                LEFT JOIN ticket_category c ON c.id = t.category_id
                WHERE %s
                ORDER BY t.created_at DESC
                LIMIT %d
                """.formatted(where, TICKET_LIMIT);
        try {
            Object[] args = "ADMIN".equals(role) || "SUPERVISOR".equals(role)
                    ? new Object[]{} : new Object[]{uid};
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ticket_no", rs.getString("ticket_no"));
                m.put("title", rs.getString("title"));
                m.put("status", rs.getString("status"));
                m.put("priority", rs.getString("priority"));
                m.put("category_name", rs.getString("category_name"));
                m.put("created_at", rs.getString("created_day"));
                String solution = rs.getString("solution");
                if (solution != null && solution.length() > SOLUTION_MAX) {
                    solution = solution.substring(0, SOLUTION_MAX) + "…";
                }
                m.put("solution", solution);
                return m;
            }, args);
        } catch (Exception e) {
            log.warn("读取 AI 近期工单失败 uid={}：{}", uid, e.getMessage());
            return List.of();
        }
    }

    /** 读取知识库全量目录（仅标题/摘要/关键词，正文由检索命中后再下发） */
    public List<Map<String, Object>> knowledgeCatalog() {
        String sql = """
                SELECT a.id, a.title, a.summary, a.keywords, c.name AS category_name
                FROM knowledge_article a
                LEFT JOIN knowledge_category c ON c.id = a.category_id
                WHERE a.status = 'PUBLISHED'
                ORDER BY a.view_count DESC, a.published_at DESC
                LIMIT %d
                """.formatted(CATALOG_LIMIT);
        try {
            return jdbc.query(sql, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("title", rs.getString("title"));
                m.put("summary", rs.getString("summary"));
                m.put("keywords", rs.getString("keywords"));
                m.put("category_name", rs.getString("category_name"));
                return m;
            });
        } catch (Exception e) {
            log.warn("读取知识库目录失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 生成个性化欢迎语：突出 AI 名字，并结合当前用户身份与待办工单 */
    public Map<String, Object> greeting(long uid, String role, String displayName) {
        String name = props.getAiService().getAssistantName();
        String who = displayName == null || displayName.isBlank() ? "" : displayName;
        Map<String, Object> p = profile(uid, role);
        int total = intOf(p.get("ticket_total"));
        int processing = intOf(p.get("ticket_processing"));

        String text;
        if ("CUSTOMER".equals(role)) {
            if (processing > 0) {
                text = "您好，%s！我是 AI 智能助手%s 😊\n您目前有 %d 条工单正在处理中，需要我帮您查进度，还是先解答问题？"
                        .formatted(who, name, processing);
            } else if (total > 0) {
                text = "您好，%s！我是 AI 智能助手%s 😊\n您的 %d 条历史工单都已处理完成，今天有什么可以帮您？"
                        .formatted(who, name, total);
            } else {
                text = "您好，%s！我是 AI 智能助手%s 😊\n账号登录、账单支付、产品使用等问题都可以问我，也可以让我帮您提交工单。"
                        .formatted(who, name);
            }
        } else {
            text = "您好，%s！我是 AI 智能助手%s 😊\n当前平台共有 %d 条工单，其中 %d 条仍在处理中，需要我帮您总结工单或起草回复吗？"
                    .formatted(who, name, total, processing);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("text", text);
        result.put("ticket_total", total);
        result.put("ticket_processing", processing);
        return result;
    }

    /** 按角色生成工单归属 WHERE 片段（列名以 t 开头，调用方需给 ticket 起别名 t） */
    private String ticketScope(long uid, String role) {
        return switch (role == null ? "" : role) {
            case "CUSTOMER" -> "t.customer_id = " + uid;
            case "AGENT" -> "t.assignee_id = " + uid;
            default -> "1 = 1";
        };
    }

    /** 安全取整数 */
    private int intOf(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 组装完整上下文（用户画像 + 近期工单 + 知识库目录） */
    public void applyContext(Map<String, Object> payload, Object userId, String role) {
        if (!(userId instanceof Number n)) {
            return;
        }
        long uid = n.longValue();
        payload.put("user_profile", profile(uid, role));
        payload.put("tickets", recentTickets(uid, role));
        payload.put("knowledge_catalog", knowledgeCatalog());
    }
}
