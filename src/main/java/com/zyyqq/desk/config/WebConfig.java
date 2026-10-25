package com.zyyqq.desk.config;

import com.zyyqq.desk.security.AuditLogInterceptor;
import com.zyyqq.desk.security.AuthInterceptor;
import com.zyyqq.desk.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 引入 Redis 后 Spring Data 进入多存储严格扫描模式，
 * 需显式声明 JDBC 仓库扫描包，否则接口无法被注册为 Bean。
 */
@EnableJdbcRepositories(basePackages = "com.zyyqq.desk.repository")
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuditLogInterceptor auditLogInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor,
                     AuditLogInterceptor auditLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.auditLogInterceptor = auditLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录限流在最外层（登录为匿名接口，需先于认证拦截器执行）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/login");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
        // 操作审计日志：注册在认证之后，保证 afterCompletion 时 AuthContext 仍可读取用户
        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
        // AI 限流在认证之后（需要用户身份）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/ai/chat");
    }

    /**
     * 头像等本地上传文件的静态访问：/uploads/** 映射到项目根目录下的 uploads/
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + Paths.get("uploads").toAbsolutePath().normalize() + "/");
    }
}
