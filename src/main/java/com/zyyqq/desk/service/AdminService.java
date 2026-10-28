package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.OperationLog;
import com.zyyqq.desk.entity.User;
import com.zyyqq.desk.repository.OperationLogRepository;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.AuthContext;
import com.zyyqq.desk.security.PasswordUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 管理后台服务：用户管理、操作日志、AI 调用日志、工单分类管理 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final OperationLogRepository logRepository;
    private final JdbcTemplate jdbc;

    /** 用户列表查询字段（含头像底色与所属公司，供管理页展示） */
    private static final String USER_SELECT =
            "SELECT id, username, display_name, role, phone, email, department, company, avatar_color, " +
                    "status, last_login_at, created_at FROM sys_user";

    /** 新建用户未填密码时使用的默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 各角色头像底色，保证同角色用户视觉统一 */
    private static final Map<String, String> AVATAR_COLORS = Map.of(
            "ADMIN", "#6366f1",
            "SUPERVISOR", "#8b5cf6",
            "AGENT", "#3b82f6",
            "CUSTOMER", "#06b6d4"
    );

    public AdminService(UserRepository userRepository, OperationLogRepository logRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.jdbc = jdbc;
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

    /** 分页查询操作审计日志 */
    public PageResult<OperationLog> logs(int page, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        List<OperationLog> list = logRepository.findPage(size, (p - 1) * size);
        long total = logRepository.countAll();
        return PageResult.of(list, total, p, size);
    }

    /** AI 调用日志：按时间倒序分页返回，供管理员监控 AI 调用 */
    public PageResult<Map<String, Object>> aiLogs(int page, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ai_task_log", Long.class);
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT id, request_id, task_type, status, duration_ms, question, answer_length, " +
                        "model, source, error_summary, created_at FROM ai_task_log ORDER BY id DESC LIMIT ? OFFSET ?",
                size, (p - 1) * size);
        return PageResult.of(list, total, p, size);
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

    /** 写入操作审计日志（失败不影响主流程） */
    private void audit(AuthContext.UserInfo u, String action, String targetType, String targetId, String detail) {
        try {
            jdbc.update("INSERT INTO operation_log(operator_id,operator_name,action,target_type,target_id,detail,created_at) VALUES(?,?,?,?,?,?,?)",
                    u.id(), u.displayName(), action, targetType, targetId, detail, LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }
}
