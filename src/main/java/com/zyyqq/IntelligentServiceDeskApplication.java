package com.zyyqq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

/**
 * 排除 Redis 仓库自动配置：项目仅使用 RedisTemplate 做缓存/限流/幂等，
 * 不使用 Spring Data Redis 仓库；同时避免其触发 Spring Data 多存储严格扫描，
 * 导致 JDBC 仓库无法注册。
 */
@SpringBootApplication(scanBasePackages = "com.zyyqq", exclude = DataRedisRepositoriesAutoConfiguration.class)
public class IntelligentServiceDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentServiceDeskApplication.class, args);
    }

}
