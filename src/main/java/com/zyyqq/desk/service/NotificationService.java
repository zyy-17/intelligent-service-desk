package com.zyyqq.desk.service;

import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.Notification;
import com.zyyqq.desk.repository.NotificationRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.stereotype.Service;

import java.util.List;

/** 站内通知服务：列表、未读数、单条/全部已读 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 分页查询当前用户的通知列表 */
    public PageResult<Notification> list(int page, int pageSize) {
        long uid = AuthContext.require().id();
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        List<Notification> list = notificationRepository.findByRecipient(uid, size, (p - 1) * size);
        long total = list.size() + (p - 1) * size; // 简化总数
        return PageResult.of(list, total, p, size);
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
