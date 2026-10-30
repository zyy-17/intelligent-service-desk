package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 站内通知接口：列表、未读数、已读标记 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 通知分页列表（附工单编号与主题，便于点击跳转详情） */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(notificationService.list(page, pageSize));
    }

    /** 未读通知数量 */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount());
    }

    /** 标记单条通知已读 */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.ok();
    }

    /** 标记全部通知已读 */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok();
    }
}
