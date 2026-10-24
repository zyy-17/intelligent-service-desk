package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.OperationLog;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireRole({"ADMIN", "SUPERVISOR"})
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String role) {
        return ApiResponse.ok(adminService.users(role));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminService.updateUserStatus(id, Integer.parseInt(String.valueOf(body.get("status"))));
        return ApiResponse.ok();
    }

    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.updateUserRole(id, body.get("role"));
        return ApiResponse.ok();
    }

    @PutMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.resetPassword(id, body.get("password"));
        return ApiResponse.ok();
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<OperationLog>> logs(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(adminService.logs(page, pageSize));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(adminService.categories());
    }

    @PostMapping("/categories")
    public ApiResponse<Void> saveCategory(@RequestBody Map<String, Object> body) {
        adminService.saveCategory(body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ApiResponse.ok();
    }
}
