package com.zyyqq.desk.service;

import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

/** 操作审计服务：统一写入 operation_log，自动带出请求真实 IP 与操作人角色 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录当前登录用户成功执行的操作 */
    public void ok(String action, String targetType, String targetId, String detail) {
        AuthContext.UserInfo u = AuthContext.get();
        if (u != null) {
            record(u.id(), u.displayName(), u.role(), action, targetType, targetId, detail, true);
        }
    }

    /** 记录当前登录用户执行的操作（可指定成功或失败） */
    public void record(String action, String targetType, String targetId, String detail, boolean success) {
        AuthContext.UserInfo u = AuthContext.get();
        if (u != null) {
            record(u.id(), u.displayName(), u.role(), action, targetType, targetId, detail, success);
        }
    }

    /** 按显式操作人写日志（登录失败等尚无登录态的场景使用） */
    public void record(Long operatorId, String operatorName, String role,
                       String action, String targetType, String targetId, String detail, boolean success) {
        try {
            jdbc.update("INSERT INTO operation_log(operator_id,operator_name,role,action,target_type,target_id,detail,status,ip,created_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    operatorId, operatorName, role, action, targetType, targetId,
                    truncate(detail, 1000), success ? "SUCCESS" : "FAILED", clientIp(), LocalDateTime.now());
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    /** 取调用方真实 IP：优先代理头，本地回环统一成 127.0.0.1 */
    private String clientIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    int comma = xff.indexOf(',');
                    return (comma > 0 ? xff.substring(0, comma) : xff).trim();
                }
                String real = req.getHeader("X-Real-IP");
                if (real != null && !real.isBlank()) {
                    return real.trim();
                }
                String remote = req.getRemoteAddr();
                if ("0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
                    return "127.0.0.1";
                }
                return remote;
            }
        } catch (Exception ignored) {
            // 非 Web 线程（定时任务）取不到请求，IP 留空
        }
        return null;
    }

    /** 详情超出列宽时截断，避免插入失败 */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
