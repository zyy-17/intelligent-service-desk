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
    private Demo demo = new Demo();

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
        /** AI 助手名称（欢迎语使用，需与 ai-service/.env 的 AI_ASSISTANT_NAME 保持一致） */
        private String assistantName = "小智";
    }

    /** 演示数据相关配置（默认全部关闭，避免影响真实数据） */
    @Data
    public static class Demo {
        private AutoTicket autoTicket = new AutoTicket();

        @Data
        public static class AutoTicket {
            /** 是否开启每天定时自动创建工单 */
            private boolean enabled = false;
            /** 执行时间（cron 表达式，默认每天 09:00） */
            private String cron = "0 0 9 * * ?";
            /** 每次生成的工单数量 */
            private int count = 5;
            /** 自动模拟客服处理并解决的比例（0-100），其余留在待分配等真人处理 */
            private int resolvePercent = 60;
        }
    }
}
