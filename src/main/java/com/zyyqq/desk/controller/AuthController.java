package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.security.AllowAnonymous;
import com.zyyqq.desk.service.AuthService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @AllowAnonymous
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.login(body.get("username"), body.get("password")));
    }

    @AllowAnonymous
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.register(body.get("username"), body.get("password"),
                body.get("displayName"), body.get("email"), body.get("role")));
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(authService.updateAvatar(file));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.ok(authService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.updateProfile(body.get("displayName"), body.get("email"), body.get("phone")));
    }
}
