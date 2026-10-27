package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.OperationLog;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理后台接口：用户、日志、AI 日志、工单分类（仅管理员/主管） */
@RestController
@RequestMapping("/api/admin")
@RequireRole({"ADMIN", "SUPERVISOR"})
public class AdminController {

    private final AdminService adminService;
    private final org.springframework.beans.factory.ObjectProvider<com.zyyqq.desk.job.AutoTicketJob> autoTicketJob;

    public AdminController(AdminService adminService,
                           org.springframework.beans.factory.ObjectProvider<com.zyyqq.desk.job.AutoTicketJob> autoTicketJob) {
        this.adminService = adminService;
        this.autoTicketJob = autoTicketJob;
    }

    /** 手动触发一次自动造单（便于立即验证定时任务，定时任务默认每天 09:00） */
    @PostMapping("/demo/auto-ticket")
    @RequireRole({"ADMIN"})
    public ApiResponse<Map<String, Object>> autoTicket(@RequestParam(defaultValue = "5") int count) {
        com.zyyqq.desk.job.AutoTicketJob job = autoTicketJob.getIfAvailable();
        if (job == null) {
            throw new com.zyyqq.desk.common.BizException(
                    "自动造单未开启，请在 application.yml 设置 app.demo.auto-ticket.enabled=true 后重启服务");
        }
        return ApiResponse.ok(job.generate(count));
    }

    /** 用户列表（可按角色过滤） */
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String role) {
        return ApiResponse.ok(adminService.users(role));
    }

    /** 更新用户启用/禁用状态 */
    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminService.updateUserStatus(id, Integer.parseInt(String.valueOf(body.get("status"))));
        return ApiResponse.ok();
    }

    /** 更新用户角色 */
    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.updateUserRole(id, body.get("role"));
        return ApiResponse.ok();
    }

    /** 重置用户密码 */
    @PutMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.resetPassword(id, body.get("password"));
        return ApiResponse.ok();
    }

    /** 操作审计日志分页 */
    @GetMapping("/logs")
    public ApiResponse<PageResult<OperationLog>> logs(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(adminService.logs(page, pageSize));
    }

    /** AI 调用日志分页 */
    @GetMapping("/ai-logs")
    public ApiResponse<PageResult<Map<String, Object>>> aiLogs(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminService.aiLogs(page, pageSize));
    }

    /** 工单分类列表 */
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(adminService.categories());
    }

    /** 新建或更新工单分类 */
    @PostMapping("/categories")
    public ApiResponse<Void> saveCategory(@RequestBody Map<String, Object> body) {
        adminService.saveCategory(body);
        return ApiResponse.ok();
    }

    /** 下架工单分类 */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ApiResponse.ok();
    }
}
