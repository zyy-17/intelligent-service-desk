package com.zyyqq.desk.service;

import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.repository.NotificationRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站内通知服务：列表、未读数、单条/全部已读 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbc;

    public NotificationService(NotificationRepository notificationRepository, JdbcTemplate jdbc) {
        this.notificationRepository = notificationRepository;
        this.jdbc = jdbc;
    }

    /**
     * 分页查询当前用户的通知列表（联表带出工单编号与主题，便于前端跳转详情）。
     * 必须用 JdbcTemplate 返回 Map：实体映射会丢弃联表扩展字段。
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize) {
        long uid = AuthContext.require().id();
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE recipient_id = ?", Long.class, uid);
        List<Map<String, Object>> raw = jdbc.queryForList(
                "SELECT n.id, n.type, n.title, n.content, n.ticket_id, n.is_read, n.created_at, " +
                        "t.ticket_no, t.title AS ticket_title, t.status AS ticket_status " +
                        "FROM notification n LEFT JOIN ticket t ON t.id = n.ticket_id " +
                        "WHERE n.recipient_id = ? ORDER BY n.created_at DESC, n.id DESC LIMIT ? OFFSET ?",
                uid, size, (p - 1) * size);
        // 显式转驼峰键名，前端统一按 ticketId / isRead / createdAt 读取
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("type", r.get("type"));
            m.put("title", r.get("title"));
            m.put("content", r.get("content"));
            m.put("ticketId", r.get("ticket_id"));
            m.put("ticketNo", r.get("ticket_no"));
            m.put("ticketTitle", r.get("ticket_title"));
            m.put("ticketStatus", r.get("ticket_status"));
            m.put("isRead", r.get("is_read"));
            m.put("createdAt", r.get("created_at"));
            list.add(m);
        }
        return PageResult.of(list, total == null ? 0 : total, p, size);
    }

    /** 未读通知数量 */
    public long unreadCount() {
        return notificationRepository.countUnread(AuthContext.require().id());
    }

    /** 标记单条通知为已读 */
    public void markRead(Long id) {
        notificationRepository.markRead(id, AuthContext.require().id());
    }

    /** 标记全部通知为已读 */
    public void markAllRead() {
        notificationRepository.markAllRead(AuthContext.require().id());
    }
}
