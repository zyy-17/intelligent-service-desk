package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.User;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.AuthContext;
import com.zyyqq.desk.security.PasswordUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理后台服务：用户管理、操作日志、AI 调用日志、工单分类管理 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    /** 用户列表查询字段（含头像底色与所属公司，供管理页展示） */
    private static final String USER_SELECT =
            "SELECT id, username, display_name, role, phone, email, department, company, avatar_color, " +
                    "status, last_login_at, created_at FROM sys_user";

    /** 新建用户未填密码时使用的默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 操作类型分组：前端按类别筛选，后端把类别翻译成 action 的 IN 条件 */
    public static final Map<String, List<String>> ACTION_GROUPS = new LinkedHashMap<>();

    static {
        ACTION_GROUPS.put("AUTH", List.of(
                "LOGIN", "LOGIN_FAILED", "REGISTER", "CHANGE_PASSWORD", "UPDATE_PROFILE", "UPDATE_AVATAR"));
        ACTION_GROUPS.put("TICKET", List.of(
                "CREATE_TICKET", "APPLY_TICKET", "REVIEW_TICKET", "ASSIGN_TICKET", "ACCEPT_TICKET",
                "REPLY_TICKET", "RESOLVE_TICKET", "CONFIRM_TICKET", "CLOSE_TICKET", "REOPEN_TICKET",
                "RATE_TICKET", "URGE_TICKET"));
        ACTION_GROUPS.put("KNOWLEDGE", List.of(
                "CREATE_ARTICLE", "UPDATE_ARTICLE", "PUBLISH_ARTICLE", "DELETE_ARTICLE"));
        ACTION_GROUPS.put("USER", List.of(
                "CREATE_USER", "UPDATE_USER", "BATCH_USER_STATUS", "UPDATE_USER_STATUS",
                "UPDATE_USER_ROLE", "RESET_PASSWORD"));
        ACTION_GROUPS.put("CATEGORY", List.of(
                "CREATE_CATEGORY", "UPDATE_CATEGORY", "UPDATE_CATEGORY_STATUS", "DELETE_CATEGORY"));
    }

    /** 各角色头像底色，保证同角色用户视觉统一 */
    private static final Map<String, String> AVATAR_COLORS = Map.of(
            "ADMIN", "#6366f1",
            "SUPERVISOR", "#8b5cf6",
            "AGENT", "#3b82f6",
            "CUSTOMER", "#06b6d4"
    );

    public AdminService(UserRepository userRepository, JdbcTemplate jdbc, AuditService auditService) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /** 按角色查询用户列表（不传角色则按 ID 升序返回全部） */
    public List<Map<String, Object>> users(String role) {
        if (role != null && !role.isBlank()) {
            return jdbc.queryForList(USER_SELECT + " WHERE role = ? ORDER BY id", role);
        }
        return jdbc.queryForList(USER_SELECT + " ORDER BY id");
    }

    /** 管理员创建用户：账号唯一校验，未指定密码时用默认密码 123456 */
    @Transactional
    public Map<String, Object> createUser(Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        String username = text(body.get("username"));
        if (username.length() < 3 || username.length() > 32) {
            throw new BizException("账号长度需 3~32 位");
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            throw new BizException("账号只能包含字母、数字和下划线");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BizException(409, "账号已存在");
        }
        // 角色白名单：管理员只能在后台分配，不接受任意字符串
        String role = text(body.get("role")).toUpperCase();
        if (!List.of("ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER").contains(role)) {
            throw new BizException("角色不合法");
        }
        String password = text(body.get("password"));
        if (password.isEmpty()) {
            password = DEFAULT_PASSWORD;
        }
        if (password.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        String displayName = text(body.get("displayName"));
        if (displayName.isEmpty()) {
            displayName = username;
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        // 头像底色按角色固定，保证同角色视觉统一（前端在色块上渲染姓名首字）
        user.setAvatarColor(AVATAR_COLORS.getOrDefault(role, "#6366f1"));
        user.setPhone(text(body.get("phone")));
        user.setEmail(text(body.get("email")));
        user.setDepartment(text(body.get("department")));
        user.setCompany(text(body.get("company")));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        audit(u, "CREATE_USER", "USER", String.valueOf(saved.getId()), "新建用户 " + username + "（" + role + "）");
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", saved.getId());
        result.put("username", username);
        result.put("defaultPassword", DEFAULT_PASSWORD);
        return result;
    }

    /** 管理员编辑用户资料（只允许改资料，账号与角色走各自专用接口） */
    @Transactional
    public void updateUser(Long id, Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        User user = userRepository.findById(id).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (body.containsKey("displayName")) {
            String displayName = text(body.get("displayName"));
            if (displayName.isEmpty()) {
                throw new BizException("姓名不能为空");
            }
            user.setDisplayName(displayName);
        }
        if (body.containsKey("phone")) {
            user.setPhone(text(body.get("phone")));
        }
        if (body.containsKey("email")) {
            user.setEmail(text(body.get("email")));
        }
        if (body.containsKey("department")) {
            user.setDepartment(text(body.get("department")));
        }
        if (body.containsKey("company")) {
            user.setCompany(text(body.get("company")));
        }
        userRepository.save(user);
        audit(u, "UPDATE_USER", "USER", String.valueOf(id), "编辑资料 " + user.getUsername());
    }

    /** 批量启用/禁用用户，跳过被操作人自己的禁用请求 */
    @Transactional
    public Map<String, Object> batchUpdateStatus(List<Long> ids, int status) {
        AuthContext.UserInfo u = AuthContext.require();
        int updated = 0;
        int skipped = 0;
        for (Long id : ids) {
            if (id.equals(u.id()) && status == 0) {
                skipped++;
                continue;
            }
            updated += jdbc.update("UPDATE sys_user SET status = ? WHERE id = ?", status, id);
        }
        audit(u, "BATCH_USER_STATUS", "USER", ids.isEmpty() ? "-" : String.valueOf(ids.get(0)),
                "批量" + (status == 1 ? "启用" : "禁用") + " " + updated + " 个用户");
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    /** 读取字符串参数并去除首尾空白，null 视为空串 */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 更新用户启用/禁用状态 */
    @Transactional
    public void updateUserStatus(Long id, int status) {
        AuthContext.UserInfo u = AuthContext.require();
        if (id.equals(u.id()) && status == 0) {
            throw new BizException("不能禁用自己的账号");
        }
        jdbc.update("UPDATE sys_user SET status = ? WHERE id = ?", status, id);
        audit(u, "UPDATE_USER_STATUS", "USER", String.valueOf(id), "状态改为 " + status);
    }

    /** 更新用户角色 */
    @Transactional
    public void updateUserRole(Long id, String role) {
        AuthContext.UserInfo u = AuthContext.require();
        jdbc.update("UPDATE sys_user SET role = ? WHERE id = ?", role, id);
        audit(u, "UPDATE_USER_ROLE", "USER", String.valueOf(id), "角色改为 " + role);
    }

    /** 管理员重置用户密码 */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AuthContext.UserInfo u = AuthContext.require();
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?", PasswordUtil.hash(newPassword), id);
        audit(u, "RESET_PASSWORD", "USER", String.valueOf(id), "重置密码");
    }

    /**
     * 分页查询操作审计日志（联表返回 Map）。
     * 注意：扩展字段（头像底色/当前角色）走 JdbcTemplate 返回 Map，
     * 用实体接会被 Spring Data JDBC 丢弃 @Transient 字段。
     *
     * @param category 操作类型分组（AUTH/TICKET/KNOWLEDGE/USER/CATEGORY），空则不限
     * @param operator 操作人关键字（姓名或账号模糊匹配）
     * @param result   执行结果（SUCCESS/FAILED），空则不限
     * @param start    起始日期（含），yyyy-MM-dd
     * @param end      结束日期（含），yyyy-MM-dd
     */
    public PageResult<Map<String, Object>> logs(int page, int pageSize, String category,
                                                String operator, String result,
                                                String start, String end) {
        int size = Math.min(Math.max(pageSize, 1), 2000);
        int p = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendLogFilters(where, args, category, operator, result, start, end);

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + LOG_FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((p - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT l.id, l.operator_id, l.operator_name, l.role, l.action, l.target_type, l.target_id, " +
                        "l.detail, l.status, l.ip, l.created_at, u.avatar_color, u.role AS current_role" +
                        LOG_FROM + where + " ORDER BY l.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(list, total == null ? 0 : total, p, size);
    }

    /** 操作日志概览：总量、今日操作、失败次数、活跃操作人，供页面顶部统计卡使用 */
    public Map<String, Object> logStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", countOf("SELECT COUNT(*) FROM operation_log"));
        m.put("today", countOf("SELECT COUNT(*) FROM operation_log WHERE created_at >= ?", LocalDate.now().atStartOfDay()));
        m.put("failed", countOf("SELECT COUNT(*) FROM operation_log WHERE status = 'FAILED'"));
        m.put("operators", countOf("SELECT COUNT(DISTINCT operator_id) FROM operation_log WHERE operator_id IS NOT NULL"));
        // 各分组条数，前端用于在类型下拉里显示数量
        Map<String, Object> groups = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : ACTION_GROUPS.entrySet()) {
            String in = "(" + String.join(",", e.getValue().stream().map(a -> "?").toList()) + ")";
            groups.put(e.getKey(), countOf("SELECT COUNT(*) FROM operation_log WHERE action IN " + in,
                    e.getValue().toArray()));
        }
        m.put("groups", groups);
        return m;
    }

    /** 日志查询的公共 FROM 片段：联表取操作人头像色与当前角色 */
    private static final String LOG_FROM = " FROM operation_log l LEFT JOIN sys_user u ON u.id = l.operator_id";

    /** 把日志筛选条件拼进 WHERE 并收集参数 */
    private void appendLogFilters(StringBuilder where, List<Object> args, String category,
                                  String operator, String result, String start, String end) {
        if (category != null && !category.isBlank()) {
            List<String> actions = ACTION_GROUPS.get(category.toUpperCase());
            if (actions != null && !actions.isEmpty()) {
                where.append(" AND l.action IN (").append(String.join(",", actions.stream().map(a -> "?").toList())).append(") ");
                args.addAll(actions);
            }
        }
        if (operator != null && !operator.isBlank()) {
            // 同时匹配操作人姓名、登录账号与被操作对象，一个输入框覆盖三种查法
            where.append(" AND (l.operator_name LIKE ? OR u.username LIKE ? OR l.target_id LIKE ?) ");
            String like = "%" + operator.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (result != null && !result.isBlank()) {
            where.append(" AND l.status = ? ");
            args.add(result.toUpperCase());
        }
        if (start != null && !start.isBlank()) {
            where.append(" AND l.created_at >= ? ");
            args.add(LocalDate.parse(start).atStartOfDay());
        }
        if (end != null && !end.isBlank()) {
            // 结束日期取当天 23:59:59，保证「选到今天」能查到今天的记录
            where.append(" AND l.created_at <= ? ");
            args.add(LocalDate.parse(end).atTime(23, 59, 59));
        }
    }

    /** 单值计数（可变参数），null 视为 0 */
    private long countOf(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    /**
     * AI 调用日志：支持类型/状态/模型/关键字/日期筛选，联表带出发起人头像色。
     *
     * @param type    任务类型（QA/SUMMARIZE/CLASSIFY/DRAFT/STEPS），空则不限
     * @param status  执行结果（SUCCESS/FAILED/FALLBACK），空则不限
     * @param model   模型名，空则不限
     * @param keyword 关键字（问题、发起人、工单号模糊匹配）
     * @param start   起始日期（含），yyyy-MM-dd
     * @param end     结束日期（含），yyyy-MM-dd
     */
    public PageResult<Map<String, Object>> aiLogs(int page, int pageSize, String type, String status,
                                                  String model, String keyword, String start, String end) {
        int size = Math.min(Math.max(pageSize, 1), 2000);
        int p = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendAiLogFilters(where, args, type, status, model, keyword, start, end);

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + AI_LOG_FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((p - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT l.id, l.request_id, l.task_type, l.user_id, l.user_name, l.user_role, l.ticket_no, " +
                        "l.status, l.duration_ms, l.question, l.answer, l.answer_length, l.model, l.source, " +
                        "l.error_summary, l.created_at, u.avatar_color, u.avatar_url" +
                        AI_LOG_FROM + where + " ORDER BY l.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(list, total == null ? 0 : total, p, size);
    }

    /** AI 日志概览：总量、成功率、平均响应、今日调用及环比，供页面顶部统计卡 */
    public Map<String, Object> aiLogStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime weekStart = todayStart.minusDays(6);
        LocalDateTime lastWeekStart = todayStart.minusDays(13);

        long total = countOf("SELECT COUNT(*) FROM ai_task_log");
        long success = countOf("SELECT COUNT(*) FROM ai_task_log WHERE status = 'SUCCESS'");
        long failed = countOf("SELECT COUNT(*) FROM ai_task_log WHERE status = 'FAILED'");
        long fallback = countOf("SELECT COUNT(*) FROM ai_task_log WHERE status = 'FALLBACK'");
        long today = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ?", todayStart);
        long yesterday = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ? AND created_at < ?",
                yesterdayStart, todayStart);
        long week = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ?", weekStart);
        long lastWeek = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ? AND created_at < ?",
                lastWeekStart, weekStart);
        long weekSuccess = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ? AND status = 'SUCCESS'", weekStart);
        long lastWeekSuccess = countOf("SELECT COUNT(*) FROM ai_task_log WHERE created_at >= ? AND created_at < ? " +
                "AND status = 'SUCCESS'", lastWeekStart, weekStart);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("success", success);
        m.put("failed", failed);
        m.put("fallback", fallback);
        m.put("successRate", rate(success, total));
        m.put("avgDuration", avgDuration(null, null));
        m.put("today", today);
        // 环比：调用量按「近 7 天 vs 前 7 天」，今日按「今天 vs 昨天」
        m.put("totalTrend", growth(week, lastWeek));
        m.put("todayTrend", growth(today, yesterday));
        m.put("successRateTrend", round1(rate(weekSuccess, week) - rate(lastWeekSuccess, lastWeek)));
        m.put("avgDurationTrend", round1(growth(avgDuration(weekStart, null), avgDuration(lastWeekStart, weekStart))));
        // 各类型计数，前端用于类型下拉显示数量
        m.put("types", jdbc.queryForList(
                "SELECT task_type AS task_type, COUNT(*) AS cnt FROM ai_task_log GROUP BY task_type"));
        m.put("models", jdbc.queryForList(
                "SELECT DISTINCT model FROM ai_task_log WHERE model IS NOT NULL AND model <> '' ORDER BY model",
                String.class));
        return m;
    }

    /** AI 日志查询的公共 FROM 片段：联表取发起人头像色与头像地址 */
    private static final String AI_LOG_FROM = " FROM ai_task_log l LEFT JOIN sys_user u ON u.id = l.user_id";

    /** 把 AI 日志筛选条件拼进 WHERE 并收集参数 */
    private void appendAiLogFilters(StringBuilder where, List<Object> args, String type, String status,
                                    String model, String keyword, String start, String end) {
        if (type != null && !type.isBlank()) {
            where.append(" AND l.task_type = ? ");
            args.add(type.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND l.status = ? ");
            args.add(status.toUpperCase());
        }
        if (model != null && !model.isBlank()) {
            where.append(" AND l.model = ? ");
            args.add(model);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 一个输入框覆盖三种查法：问题内容、发起人姓名、关联工单号
            where.append(" AND (l.question LIKE ? OR l.user_name LIKE ? OR l.ticket_no LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (start != null && !start.isBlank()) {
            where.append(" AND l.created_at >= ? ");
            args.add(LocalDate.parse(start).atStartOfDay());
        }
        if (end != null && !end.isBlank()) {
            where.append(" AND l.created_at <= ? ");
            args.add(LocalDate.parse(end).atTime(23, 59, 59));
        }
    }

    /** 计算百分比成功率，分母为 0 时返回 0 */
    private double rate(long part, long total) {
        return total == 0 ? 0 : round1(part * 100.0 / total);
    }

    /** 保留一位小数 */
    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** 计算环比增长率（%），基准为 0 时按 100% 处理避免除零 */
    private double growth(double current, double base) {
        if (base <= 0) {
            return current <= 0 ? 0 : 100.0;
        }
        return round1((current - base) * 100.0 / base);
    }

    /** 平均响应时间（毫秒），可传入时间区间，区间为空则统计全部 */
    private double avgDuration(LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("SELECT AVG(duration_ms) FROM ai_task_log WHERE duration_ms IS NOT NULL");
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" AND created_at >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND created_at < ?");
            args.add(to);
        }
        Double v = jdbc.queryForObject(sql.toString(), Double.class, args.toArray());
        return v == null ? 0 : round1(v);
    }

    /** 查询工单分类列表（含说明与状态，供管理页展示） */
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList(
                "SELECT id, name, description, color, sla_hours, sort, status FROM ticket_category ORDER BY sort, id");
    }

    /** 新建或更新工单分类 */
    @Transactional
    public void saveCategory(Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) {
            throw new BizException("分类名称不能为空");
        }
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        // 兼容前端两种键名：老表单提交 sla_hours，新代码用驼峰 slaHours
        int slaHours = Integer.parseInt(firstNonNull(body, "slaHours", "sla_hours", "24"));
        String color = String.valueOf(body.getOrDefault("color", "#6366f1"));
        int sort = Integer.parseInt(String.valueOf(body.getOrDefault("sort", "0")));
        String description = text(body.get("description"));
        if (description.length() > 200) {
            description = description.substring(0, 200);
        }
        if (id == null) {
            jdbc.update("INSERT INTO ticket_category(name,description,color,sla_hours,sort,status) VALUES(?,?,?,?,?,1)",
                    name, blankToNull(description), color, slaHours, sort);
            audit(u, "CREATE_CATEGORY", "CATEGORY", name, "新建分类");
        } else {
            jdbc.update("UPDATE ticket_category SET name=?, description=?, color=?, sla_hours=?, sort=? WHERE id=?",
                    name, blankToNull(description), color, slaHours, sort, id);
            audit(u, "UPDATE_CATEGORY", "CATEGORY", name, "更新分类");
        }
    }

    /** 启用或停用工单分类（停用后新建工单时不再可选） */
    @Transactional
    public void updateCategoryStatus(Long id, int status) {
        AuthContext.UserInfo u = AuthContext.require();
        jdbc.update("UPDATE ticket_category SET status = ? WHERE id = ?", status, id);
        audit(u, "UPDATE_CATEGORY_STATUS", "CATEGORY", String.valueOf(id), status == 1 ? "启用分类" : "停用分类");
    }

    /** 下架（软删除）工单分类 */
    @Transactional
    public void deleteCategory(Long id) {
        AuthContext.UserInfo u = AuthContext.require();
        jdbc.update("UPDATE ticket_category SET status = 0 WHERE id = ?", id);
        audit(u, "DELETE_CATEGORY", "CATEGORY", String.valueOf(id), "下架分类");
    }

    /** 空串转 null，避免库里出现空字符串与 NULL 两种「无说明」形态 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 按顺序取第一个存在的键值，取不到时返回默认值 */
    private static String firstNonNull(Map<String, Object> body, String keyA, String keyB, String fallback) {
        Object a = body.get(keyA);
        if (a != null && !String.valueOf(a).isBlank()) {
            return String.valueOf(a);
        }
        Object b = body.get(keyB);
        if (b != null && !String.valueOf(b).isBlank()) {
            return String.valueOf(b);
        }
        return fallback;
    }

    /** 写入操作审计日志（统一走 AuditService，自动带出 IP 与角色快照） */
    private void audit(AuthContext.UserInfo u, String action, String targetType, String targetId, String detail) {
        auditService.record(u.id(), u.displayName(), u.role(), action, targetType, targetId, detail, true);
    }
}
