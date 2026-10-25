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

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
