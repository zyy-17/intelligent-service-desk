package com.zyyqq.desk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Ai ai = new Ai();
    private AiService aiService = new AiService();

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
