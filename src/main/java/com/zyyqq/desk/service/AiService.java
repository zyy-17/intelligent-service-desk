package com.zyyqq.desk.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.repository.AiTaskLogRepository;
import com.zyyqq.desk.repository.KnowledgeArticleRepository;
import com.zyyqq.desk.entity.AiTaskLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能问答：优先调用独立 FastAPI AI 服务（承担模型调用、知识检索、摘要、分类、回复草稿等能力）。
 * FastAPI 服务不可用时，降级为本地知识库关键词检索，保证核心问答可演示。
 */
@Slf4j
@Service
public class AiService {

    private final AppProperties props;
    private final KnowledgeArticleRepository articleRepository;
    private final AiTaskLogRepository aiLogRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AiService(AppProperties props, KnowledgeArticleRepository articleRepository,
                     AiTaskLogRepository aiLogRepository, JdbcTemplate jdbc) {
        this.props = props;
        this.articleRepository = articleRepository;
        this.aiLogRepository = aiLogRepository;
        this.jdbc = jdbc;
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        String sessionId = body.get("session_id") == null ? null : String.valueOf(body.get("session_id"));
        Object userId = body.get("user_id");
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();
        if (question == null || question.isBlank()) {
            return Map.of("answer", "请输入您的问题", "source", "无", "fallback", true);
        }

        // 1. 本地知识库检索（用于组装传给 FastAPI 的上下文，以及降级兜底）
        List<KnowledgeArticle> hits = articleRepository.search(question.trim(), 5);
        if (hits.isEmpty()) {
            hits = fuzzySearch(question.trim());
        }

        // 2. 优先调用 FastAPI AI 服务
        AppProperties.AiService aiService = props.getAiService();
        if (aiService.getBaseUrl() != null && !aiService.getBaseUrl().isBlank()) {
            try {
                Map<String, Object> result = callFastApi(question, sessionId, userId, hits, aiService);
                long duration = System.currentTimeMillis() - start;
                saveLog(requestId, "QA", "SUCCESS", (int) duration, question,
                        String.valueOf(result.get("answer")).length(),
                        (String) result.get("model"), (String) result.get("source"), null);
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                saveLog(requestId, "QA", "FAILED", (int) duration, question, 0, "fastapi", null, e.getMessage());
                log.warn("FastAPI AI 服务调用失败，降级为本地知识库检索：{}", e.getMessage());
            }
        }

        // 3. 降级：本地知识库检索
        long duration = System.currentTimeMillis() - start;
        String answer = buildFallbackAnswer(question, hits);
        String source = hits.isEmpty() ? "未找到匹配知识" : hits.get(0).getTitle();
        saveLog(requestId, "QA", "FALLBACK", (int) duration, question, answer.length(), "knowledge-base", source, null);
        return Map.of("answer", answer, "source", source, "references", brief(hits), "fallback", true);
    }

    /**
     * 调用 FastAPI AI 服务的 /api/ai/chat 接口。
     * 将本地检索到的知识作为 knowledge 上下文传给 AI 服务，由其完成模型调用与增强生成。
     */
    private Map<String, Object> callFastApi(String question, String sessionId, Object userId,
                                            List<KnowledgeArticle> hits,
                                            AppProperties.AiService aiService) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question);
        payload.put("knowledge", knowledgePayload(hits));
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("session_id", sessionId);
        }
        if (userId != null) {
            payload.put("user_id", userId);
        }

        String url = aiService.getBaseUrl().replaceAll("/+$", "") + "/api/ai/chat";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(aiService.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("AI 服务返回 " + resp.statusCode());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        String answer = root.path("answer").asText();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("AI 服务返回空答案");
        }
        String source = root.path("source").asText();
        boolean fallback = root.path("fallback").asBoolean(false);
        String model = root.path("model").asText(null);
        String respSessionId = root.path("session_id").asText(null);
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer.trim());
        result.put("source", source == null || source.isBlank() ? "模型生成" : source);
        result.put("fallback", fallback);
        if (model != null && !model.isBlank()) {
            result.put("model", model);
        }
        if (respSessionId != null && !respSessionId.isBlank()) {
            result.put("session_id", respSessionId);
        }
        result.put("references", brief(hits));
        return result;
    }

    /**
     * 通用代理：把会话管理请求转发给 FastAPI AI 服务。
     * AI 服务不可用时抛出异常，由全局异常处理返回提示。
     */
    public Object proxy(String method, String pathWithQuery, Map<String, Object> body) {
        AppProperties.AiService aiService = props.getAiService();
        if (aiService.getBaseUrl() == null || aiService.getBaseUrl().isBlank()) {
            throw new com.zyyqq.desk.common.BizException("AI 服务未配置");
        }
        try {
            String url = aiService.getBaseUrl().replaceAll("/+$", "") + pathWithQuery;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(aiService.getTimeoutSeconds()))
                    .header("Content-Type", "application/json");
            if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method.toUpperCase(),
                        HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body == null ? Map.of() : body)));
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new com.zyyqq.desk.common.BizException("AI 服务返回 " + resp.statusCode());
            }
            if (resp.body() == null || resp.body().isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(resp.body(), Object.class);
        } catch (com.zyyqq.desk.common.BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 服务代理调用失败({} {})：{}", method, pathWithQuery, e.getMessage());
            throw new com.zyyqq.desk.common.BizException("AI 服务暂时不可用，请确认已启动 ai-service");
        }
    }

    private List<Map<String, Object>> knowledgePayload(List<KnowledgeArticle> hits) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (KnowledgeArticle a : hits) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("summary", a.getSummary());
            m.put("body", a.getBody());
            m.put("keywords", a.getKeywords());
            list.add(m);
        }
        return list;
    }

    private List<KnowledgeArticle> fuzzySearch(String question) {
        // 将问题切分为 2 字短语，逐个短语做 LIKE 匹配，取命中次数最多的文章
        java.util.Map<Long, Integer> score = new java.util.LinkedHashMap<>();
        java.util.Set<String> grams = new java.util.LinkedHashSet<>();
        String q = question.replaceAll("[\\s，。！？、,.!?：:；;]", "");
        for (int i = 0; i + 1 < q.length(); i++) {
            grams.add(q.substring(i, i + 2));
        }
        if (grams.isEmpty()) {
            return java.util.List.of();
        }
        for (String g : grams) {
            List<KnowledgeArticle> matched = articleRepository.search(g, 10);
            for (KnowledgeArticle a : matched) {
                score.merge(a.getId(), 1, Integer::sum);
            }
        }
        if (score.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Long> ids = score.entrySet().stream()
                .filter(e -> e.getValue() >= 2)  // 至少 2 个短语命中才认为相关
                .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                .limit(5)
                .map(java.util.Map.Entry::getKey)
                .toList();
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<KnowledgeArticle> result = new java.util.ArrayList<>();
        for (Long id : ids) {
            articleRepository.findById(id).ifPresent(result::add);
        }
        return result;
    }

    private String buildFallbackAnswer(String question, List<KnowledgeArticle> hits) {
        if (hits.isEmpty()) {
            return "抱歉，未在知识库中找到与「" + question + "」直接相关的答案。\n\n建议您：\n1. 换个关键词重新描述问题；\n2. 提交工单，由人工客服为您处理；\n3. 查看知识库热门文章获取帮助。";
        }
        KnowledgeArticle top = hits.get(0);
        String body = top.getBody() == null ? "" : top.getBody().replaceAll("(?s)#{1,6}\\s*|\\*|>`?|`", "").trim();
        StringBuilder sb = new StringBuilder();
        sb.append("根据知识库文章《").append(top.getTitle()).append("》，为您解答：\n\n");
        sb.append(body.length() > 800 ? body.substring(0, 800) + "…" : body);
        if (hits.size() > 1) {
            sb.append("\n\n更多相关内容：");
            for (int i = 1; i < Math.min(hits.size(), 4); i++) {
                sb.append("\n· ").append(hits.get(i).getTitle());
            }
        }
        sb.append("\n\n（当前为本地知识库检索结果，启动 FastAPI AI 服务后可获得更智能的回答。）");
        return sb.toString();
    }

    private List<Map<String, Object>> brief(List<KnowledgeArticle> hits) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (KnowledgeArticle a : hits) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("categoryName", a.getCategoryName());
            r.add(m);
        }
        return r;
    }

    private void saveLog(String requestId, String taskType, String status, int duration, String question,
                         Integer answerLength, String model, String source, String error) {
        try {
            AiTaskLog log = new AiTaskLog();
            log.setRequestId(requestId);
            log.setTaskType(taskType);
            log.setStatus(status);
            log.setDurationMs(duration);
            log.setQuestion(truncate(question, 500));
            log.setAnswerLength(answerLength);
            log.setModel(model);
            log.setSource(source);
            log.setErrorSummary(error == null ? null : truncate(error, 500));
            log.setCreatedAt(LocalDateTime.now());
            aiLogRepository.save(log);
        } catch (Exception e) {
            // 记录失败不影响主流程
            log.warn("AI 日志写入失败", e);
        }
    }

    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n);
    }
}
