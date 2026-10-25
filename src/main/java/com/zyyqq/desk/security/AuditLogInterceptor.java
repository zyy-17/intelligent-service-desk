package com.zyyqq.desk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 操作审计日志拦截器：对增删改操作记录 INFO 日志（操作人/方法/路径/耗时/结果） */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private static final String ATTR_START = AuditLogInterceptor.class.getName() + ".start";

    /** 写操作开始前记录起始时间 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            request.setAttribute(ATTR_START, System.currentTimeMillis());
        }
        return true;
    }

    /** 请求完成后输出审计日志 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startObj = request.getAttribute(ATTR_START);
        if (startObj == null) {
            return;
        }
        long cost = System.currentTimeMillis() - (Long) startObj;
        String method = request.getMethod();
        String path = request.getRequestURI();
        String user = "匿名";
        AuthContext.UserInfo u = AuthContext.get();
        if (u != null) {
            user = u.username() + "(" + u.displayName() + ")[role=" + u.role() + "]";
        }
        String action = actionName(method);
        String result = ex == null ? "成功" : "失败";
        if (ex == null && response.getStatus() >= 400) {
            result = "HTTP " + response.getStatus();
        }
        log.info("[{}] 操作人={} 方法={} 路径={} 耗时={}ms 结果={}",
                action, user, method, path, cost, result);
    }

    /** 把 HTTP 方法映射为中文操作名 */
    private String actionName(String method) {
        switch (method.toUpperCase()) {
            case "POST":
                return "新增";
            case "PUT":
            case "PATCH":
                return "修改";
            case "DELETE":
                return "删除";
            default:
                return method;
        }
    }
}
