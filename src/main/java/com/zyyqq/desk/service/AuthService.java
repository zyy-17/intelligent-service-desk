package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.entity.User;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.JwtUtil;
import com.zyyqq.desk.security.PasswordUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("账号或密码错误"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            throw BizException.unauthorized("账号或密码错误");
        }
        jdbc.update("UPDATE sys_user SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), user.getId());
        String token = jwtUtil.create(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getStatus());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toMap(user));
        return data;
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String displayName, String email, String role) {
        if (username == null || username.trim().length() < 3) {
            throw new BizException("账号至少 3 位");
        }
        if (password == null || password.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        // 身份白名单：注册仅允许选择这三类，ADMIN 只能由管理员在系统管理中分配
        if (role == null || !List.of("CUSTOMER", "AGENT", "SUPERVISOR").contains(role)) {
            role = "CUSTOMER";
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BizException(409, "账号已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
        user.setRole(role);
        user.setEmail(email);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        String token = jwtUtil.create(saved.getId(), saved.getUsername(), saved.getDisplayName(), saved.getRole(), saved.getStatus());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toMap(saved));
        return data;
    }

    public Map<String, Object> profile() {
        User user = userRepository.findById(com.zyyqq.desk.security.AuthContext.require().id())
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        return toMap(user);
    }

    public Map<String, Object> updateProfile(String displayName, String email, String phone) {
        long uid = com.zyyqq.desk.security.AuthContext.require().id();
        jdbc.update("UPDATE sys_user SET display_name = COALESCE(?, display_name), email = COALESCE(?, email), phone = COALESCE(?, phone) WHERE id = ?",
                displayName, email, phone, uid);
        return profile();
    }

    public static Map<String, Object> toMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        m.put("role", u.getRole());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        m.put("company", u.getCompany());
        m.put("department", u.getDepartment());
        m.put("avatarColor", u.getAvatarColor());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    /**
     * 保存头像文件到本地 uploads/avatar 目录，并更新用户记录。
     */
    public Map<String, Object> updateAvatar(org.springframework.web.multipart.MultipartFile file) {
        long uid = com.zyyqq.desk.security.AuthContext.require().id();
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择头像图片");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.')).toLowerCase() : ".png";
        if (!List.of(".png", ".jpg", ".jpeg", ".gif", ".webp").contains(ext)) {
            throw new BizException("仅支持 png/jpg/jpeg/gif/webp 图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BizException("头像图片不能超过 5MB");
        }
        try {
            // 使用绝对路径，避免 multipart transferTo 相对路径落到 Tomcat 临时目录
            java.nio.file.Path dir = java.nio.file.Paths.get("uploads", "avatar").toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(dir);
            // 先清理该用户旧头像，避免文件堆积
            if (java.nio.file.Files.isDirectory(dir)) {
                try (var stream = java.nio.file.Files.list(dir)) {
                    stream.filter(p -> p.getFileName().toString().startsWith("avatar_" + uid + "_"))
                            .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) { } });
                }
            }
            String filename = "avatar_" + uid + "_" + System.currentTimeMillis() + ext;
            java.nio.file.Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
            String url = "/uploads/avatar/" + filename;
            jdbc.update("UPDATE sys_user SET avatar_url = ? WHERE id = ?", url, uid);
            return profile();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("头像保存失败：" + e.getMessage());
        }
    }
}
