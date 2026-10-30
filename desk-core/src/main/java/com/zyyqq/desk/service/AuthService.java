package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.config.AppProperties;
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

/** 认证服务：登录、注册、个人信息与头像、密码管理 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;
    private final RedisSupport redisSupport;
    private final AppProperties props;
    private final MenuService menuService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, JdbcTemplate jdbc,
                       RedisSupport redisSupport, AppProperties props, MenuService menuService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
        this.redisSupport = redisSupport;
        this.props = props;
        this.menuService = menuService;
        this.auditService = auditService;
    }

    /** 登录：校验账号密码、失败锁定、签发 JWT 并返回用户信息与角色菜单 */
    public Map<String, Object> login(String username, String password) {
        // 登录安全：短时间内连续失败达到阈值则锁定（Redis 计数；Redis 不可用时降级放行并记录日志）
        AppProperties.Redis rc = props.getRedis();
        long failures = redisSupport.recordLoginFailure(username, rc.getLoginFailWindowSeconds());
        if (failures > rc.getLoginMaxFailures()) {
            auditFailure(username, null, "账号已临时锁定，连续失败 " + failures + " 次");
            throw new com.zyyqq.desk.common.BizException(429,
                    "登录失败次数过多，账号已临时锁定，请 " + (rc.getLoginFailWindowSeconds() / 60) + " 分钟后再试");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            auditFailure(username, null, "账号不存在");
            throw BizException.unauthorized("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            auditFailure(username, user, "账号已被禁用");
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            auditFailure(username, user, "密码错误");
            throw BizException.unauthorized("账号或密码错误");
        }
        redisSupport.clearLoginFailures(username);
        jdbc.update("UPDATE sys_user SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), user.getId());
        String token = jwtUtil.create(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getStatus());
        auditService.record(user.getId(), user.getDisplayName(), user.getRole(),
                "LOGIN", "USER", user.getUsername(), "登录成功", true);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toMap(user));
        data.put("menus", menuService.menusFor(user.getRole()));
        return data;
    }

    /** 登录失败留痕：能定位到用户时带上真实姓名与角色，查无此人则只记账号 */
    private void auditFailure(String username, User user, String reason) {
        if (user != null) {
            auditService.record(user.getId(), user.getDisplayName(), user.getRole(),
                    "LOGIN_FAILED", "USER", username, "登录失败：" + reason, false);
        } else {
            auditService.record(null, username, null, "LOGIN_FAILED", "USER", username,
                    "登录失败：" + reason, false);
        }
    }

    /** 注册新账号（身份白名单，ADMIN 仅管理员分配） */
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
        // 默认头像：无图片，前端用「昵称首字 + 随机底色」渲染
        String[] palette = {"#6366f1", "#3b82f6", "#22c55e", "#f59e0b", "#ec4899", "#8b5cf6", "#06b6d4", "#ef4444"};
        user.setAvatarColor(palette[java.util.concurrent.ThreadLocalRandom.current().nextInt(palette.length)]);
        user.setEmail(email);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        String token = jwtUtil.create(saved.getId(), saved.getUsername(), saved.getDisplayName(), saved.getRole(), saved.getStatus());
        auditService.record(saved.getId(), saved.getDisplayName(), saved.getRole(),
                "REGISTER", "USER", saved.getUsername(), "注册新账号（" + saved.getRole() + "）", true);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toMap(saved));
        data.put("menus", menuService.menusFor(saved.getRole()));
        return data;
    }

    /** 查询当前登录用户个人信息 */
    public Map<String, Object> profile() {
        User user = userRepository.findById(com.zyyqq.desk.security.AuthContext.require().id())
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        return toMap(user);
    }

    /** 返回当前登录用户可见的侧边栏菜单（前端启动时刷新菜单缓存用） */
    public List<Map<String, Object>> menus() {
        return menuService.menusFor(com.zyyqq.desk.security.AuthContext.require().role());
    }

    /** 个人中心统计：按角色返回工单口径（我提交/我负责/全站）与我的知识收藏数 */
    public Map<String, Object> profileStats() {
        long uid = com.zyyqq.desk.security.AuthContext.require().id();
        String role = com.zyyqq.desk.security.AuthContext.require().role();
        Map<String, Object> m = new HashMap<>();
        if ("CUSTOMER".equals(role)) {
            m.put("scope", "MINE_CUSTOMER");
            m.put("total", count("SELECT COUNT(*) FROM ticket WHERE customer_id = ?", uid));
            m.put("resolved", count("SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status IN ('RESOLVED','CLOSED')", uid));
            m.put("pending", count("SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status NOT IN ('RESOLVED','CLOSED')", uid));
        } else if ("AGENT".equals(role)) {
            m.put("scope", "MINE_AGENT");
            m.put("total", count("SELECT COUNT(*) FROM ticket WHERE assignee_id = ?", uid));
            m.put("resolved", count("SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status IN ('RESOLVED','CLOSED')", uid));
            m.put("pending", count("SELECT COUNT(*) FROM ticket WHERE assignee_id = ? AND status NOT IN ('RESOLVED','CLOSED')", uid));
        } else {
            // 主管与管理员看全站口径
            m.put("scope", "ALL");
            m.put("total", count("SELECT COUNT(*) FROM ticket"));
            m.put("resolved", count("SELECT COUNT(*) FROM ticket WHERE status IN ('RESOLVED','CLOSED')"));
            m.put("pending", count("SELECT COUNT(*) FROM ticket WHERE status NOT IN ('RESOLVED','CLOSED')"));
        }
        m.put("favoriteCount", count("SELECT COUNT(*) FROM knowledge_favorite WHERE user_id = ?", uid));
        return m;
    }

    /** 单值计数查询 */
    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    /** 更新个人资料（昵称/邮箱/手机号） */
    public Map<String, Object> updateProfile(String displayName, String email, String phone) {
        long uid = com.zyyqq.desk.security.AuthContext.require().id();
        jdbc.update("UPDATE sys_user SET display_name = COALESCE(?, display_name), email = COALESCE(?, email), phone = COALESCE(?, phone) WHERE id = ?",
                displayName, email, phone, uid);
        auditService.ok("UPDATE_PROFILE", "USER", String.valueOf(uid), "修改个人资料");
        return profile();
    }

    /** 修改密码：校验原密码，新密码至少 6 位 */
    public void changePassword(String oldPassword, String newPassword) {
        long uid = com.zyyqq.desk.security.AuthContext.require().id();
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        User user = userRepository.findById(uid).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (oldPassword == null || !PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            auditService.record("CHANGE_PASSWORD", "USER", String.valueOf(uid), "修改密码失败：原密码错误", false);
            throw new BizException("原密码错误");
        }
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?", PasswordUtil.hash(newPassword), uid);
        auditService.ok("CHANGE_PASSWORD", "USER", String.valueOf(uid), "修改登录密码");
    }

    /** 用户实体转 Map（脱敏返回给前端） */
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
        m.put("createdAt", u.getCreatedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("status", u.getStatus());
        return m;
    }

    /** 上传头像文件到本地 uploads/avatar 目录并更新用户记录 */
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
            auditService.ok("UPDATE_AVATAR", "USER", String.valueOf(uid), "更新头像");
            return profile();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("头像保存失败：" + e.getMessage());
        }
    }
}
