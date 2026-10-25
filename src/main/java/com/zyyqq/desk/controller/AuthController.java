package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.security.AllowAnonymous;
import com.zyyqq.desk.service.AuthService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 认证接口：登录、注册、头像上传、个人信息与密码 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 用户登录 */
    @AllowAnonymous
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.login(body.get("username"), body.get("password")));
    }

    /** 用户注册 */
    @AllowAnonymous
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.register(body.get("username"), body.get("password"),
                body.get("displayName"), body.get("email"), body.get("role")));
    }

    /** 上传头像 */
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(authService.updateAvatar(file));
    }

    /** 查询当前用户可见菜单（前端启动时刷新菜单缓存） */
    @GetMapping("/menus")
    public ApiResponse<java.util.List<Map<String, Object>>> menus() {
        return ApiResponse.ok(authService.menus());
    }

    /** 查询当前用户信息 */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.ok(authService.profile());
    }

    /** 更新个人资料 */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.updateProfile(body.get("displayName"), body.get("email"), body.get("phone")));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.ok();
    }
}
