package com.zyyqq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用启动类：排除 Redis 仓库自动配置，避免多存储严格扫描；开启定时任务（演示造单） */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.zyyqq", exclude = DataRedisRepositoriesAutoConfiguration.class)
public class IntelligentServiceDeskApplication {

    /** 应用启动入口 */
    public static void main(String[] args) {
        SpringApplication.run(IntelligentServiceDeskApplication.class, args);
    }

}
