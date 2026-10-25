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

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final OperationLogRepository logRepository;
    private final JdbcTemplate jdbc;

    public AdminService(UserRepository userRepository, OperationLogRepository logRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> users(String role) {
        if (role != null && !role.isBlank()) {
            return jdbc.queryForList(
                    "SELECT id, username, display_name, role, phone, email, department, status, last_login_at, created_at " +
                            "FROM sys_user WHERE role = ? ORDER BY id", role);
        }
        return jdbc.queryForList(
                "SELECT id, username, display_name, role, phone, email, department, status, last_login_at, created_at " +
                        "FROM sys_user ORDER BY role, id");
    }

    @Transactional
    public void updateUserStatus(Long id, int status) {
        AuthContext.UserInfo u = AuthContext.require();
        if (id.equals(u.id()) && status == 0) {
            throw new BizException("不能禁用自己的账号");
        }
        jdbc.update("UPDATE sys_user SET status = ? WHERE id = ?", status, id);
        audit(u, "UPDATE_USER_STATUS", "USER", String.valueOf(id), "状态改为 " + status);
    }

    @Transactional
    public void updateUserRole(Long id, String role) {
        AuthContext.UserInfo u = AuthContext.require();
        jdbc.update("UPDATE sys_user SET role = ? WHERE id = ?", role, id);
        audit(u, "UPDATE_USER_ROLE", "USER", String.valueOf(id), "角色改为 " + role);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AuthContext.UserInfo u = AuthContext.require();
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?", PasswordUtil.hash(newPassword), id);
        audit(u, "RESET_PASSWORD", "USER", String.valueOf(id), "重置密码");
    }

    public PageResult<OperationLog> logs(int page, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        List<OperationLog> list = logRepository.findPage(size, (p - 1) * size);
        long total = logRepository.countAll();
        return PageResult.of(list, total, p, size);
    }

    /**
     * AI 调用日志：按时间倒序分页返回 ai_task_log 记录，供管理员监控 AI 调用情况。
     */
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

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, color, sla_hours, sort, status FROM ticket_category ORDER BY sort");
    }

    @Transactional
    public void saveCategory(Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        String name = String.valueOf(body.getOrDefault("name", ""));
        if (name.isBlank()) {
            throw new BizException("分类名称不能为空");
        }
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        int slaHours = Integer.parseInt(String.valueOf(body.getOrDefault("slaHours", "24")));
        String color = String.valueOf(body.getOrDefault("color", "#6366f1"));
        int sort = Integer.parseInt(String.valueOf(body.getOrDefault("sort", "0")));
        if (id == null) {
            jdbc.update("INSERT INTO ticket_category(name,color,sla_hours,sort,status) VALUES(?,?,?,?,1)", name, color, slaHours, sort);
            audit(u, "CREATE_CATEGORY", "CATEGORY", name, "新建分类");
        } else {
            jdbc.update("UPDATE ticket_category SET name=?, color=?, sla_hours=?, sort=? WHERE id=?", name, color, slaHours, sort, id);
            audit(u, "UPDATE_CATEGORY", "CATEGORY", name, "更新分类");
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        AuthContext.UserInfo u = AuthContext.require();
        jdbc.update("UPDATE ticket_category SET status = 0 WHERE id = ?", id);
        audit(u, "DELETE_CATEGORY", "CATEGORY", String.valueOf(id), "下架分类");
    }

    private void audit(AuthContext.UserInfo u, String action, String targetType, String targetId, String detail) {
        try {
            jdbc.update("INSERT INTO operation_log(operator_id,operator_name,action,target_type,target_id,detail,created_at) VALUES(?,?,?,?,?,?,?)",
                    u.id(), u.displayName(), action, targetType, targetId, detail, LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }
}
