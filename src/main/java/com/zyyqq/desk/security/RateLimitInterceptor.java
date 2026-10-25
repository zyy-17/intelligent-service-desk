package com.zyyqq.desk.security;

import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.service.RedisSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口访问限流拦截器（Redis 计数）。
 * 保护对象：登录接口（按 IP）、AI 问答接口（按用户）。
 * 降级策略：Redis 不可用时放行并记录日志，避免限流组件故障阻断核心业务。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisSupport redisSupport;
    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public RateLimitInterceptor(RedisSupport redisSupport, AppProperties props) {
        this.redisSupport = redisSupport;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && path.endsWith("/api/auth/login")) {
            String ip = clientIp(request);
            long count = redisSupport.rateLimitIncr("ratelimit", "login:" + ip,
                    props.getRedis().getRateLimitWindowSeconds());
            if (count >= 0 && count > props.getRedis().getLoginRateLimit()) {
                log.warn("登录接口触发限流：IP={} 窗口内请求数={}", ip, count);
                return reject(response, "登录请求过于频繁，请 1 分钟后再试");
            }
            return true;
        }

        if ("POST".equals(method) && path.endsWith("/api/ai/chat")) {
            String user = String.valueOf(AuthContext.require().id());
            long count = redisSupport.rateLimitIncr("ratelimit", "ai:" + user,
                    props.getRedis().getRateLimitWindowSeconds());
            if (count >= 0 && count > props.getRedis().getAiRateLimit()) {
                log.warn("AI 问答接口触发限流：userId={} 窗口内请求数={}", user, count);
                return reject(response, "提问太频繁啦，请稍后再试");
            }
            return true;
        }

        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 429);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(mapper.writeValueAsString(body));
        return false;
    }
}
