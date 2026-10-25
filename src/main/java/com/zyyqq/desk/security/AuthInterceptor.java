package com.zyyqq.desk.security;

import tools.jackson.databind.ObjectMapper;
import com.zyyqq.desk.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/** 认证 + 角色校验拦截器：校验 JWT 签名、账号状态与角色权限 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    /** 请求前校验登录态与角色权限 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        // 匿名接口放行
        if (method.hasMethodAnnotation(AllowAnonymous.class)) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, 401, "未登录或登录已过期");
            return false;
        }
        String token = auth.substring(7);
        Map<String, Object> claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            writeError(response, 401, "登录凭证无效或已过期");
            return false;
        }

        // 账号状态校验（禁用后立即失效）
        Object status = claims.get("status");
        if (status != null && "0".equals(String.valueOf(status))) {
            writeError(response, 403, "账号已被禁用");
            return false;
        }

        AuthContext.set(new AuthContext.UserInfo(
                Long.valueOf(String.valueOf(claims.get("uid"))),
                String.valueOf(claims.get("username")),
                String.valueOf(claims.get("displayName")),
                String.valueOf(claims.get("role"))
        ));

        // 角色校验（方法级优先，其次类级）
        RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            String role = String.valueOf(claims.get("role"));
            boolean ok = false;
            for (String r : requireRole.value()) {
                if (r.equals(role)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                writeError(response, 403, "没有操作权限");
                return false;
            }
        }
        return true;
    }

    /** 请求完成后清理认证上下文 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    /** 写出错误响应（JSON 格式） */
    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("code", code);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
