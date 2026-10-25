package com.zyyqq.desk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 应用业务配置（JWT/AI/Redis 参数） */
@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Ai ai = new Ai();
    private AiService aiService = new AiService();
    private Redis redis = new Redis();

    @Data
    public static class Redis {
        /** 统一 Key 前缀，用于区分缓存/认证/限流/幂等数据 */
        private String keyPrefix = "isd";
        private int loginRateLimit = 10;
        private int aiRateLimit = 20;
        private int rateLimitWindowSeconds = 60;
        private int loginMaxFailures = 5;
        private int loginFailWindowSeconds = 900;
        private int hotArticlesTtl = 300;
        private int categoriesTtl = 600;
        private int statsOverviewTtl = 60;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expireSeconds = 86400;
    }

    @Data
    public static class Ai {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private String model;
        private int timeoutSeconds = 45;
        private double temperature = 0.3;
    }

    @Data
    public static class AiService {
        /** FastAPI AI 服务地址 */
        private String baseUrl = "http://localhost:8000";
        private int timeoutSeconds = 50;
    }
}
