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

/** 智能问答：优先调用 FastAPI AI 服务，失败时降级本地知识库检索 */
@Slf4j
@Service
public class AiService {

    private final AppProperties props;
    private final KnowledgeArticleRepository articleRepository;
    private final AiTaskLogRepository aiLogRepository;
    private final AiContextService contextService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AiService(AppProperties props, KnowledgeArticleRepository articleRepository,
                     AiTaskLogRepository aiLogRepository, AiContextService contextService, JdbcTemplate jdbc) {
        this.props = props;
        this.articleRepository = articleRepository;
        this.aiLogRepository = aiLogRepository;
        this.contextService = contextService;
        this.jdbc = jdbc;
    }

    /** 生成个性化欢迎语（突出 AI 名字，结合用户身份与工单概况） */
    public Map<String, Object> greeting(long uid, String role, String displayName) {
        return contextService.greeting(uid, role, displayName);
    }

    /** 非流式问答：检索知识库、调用 AI 服务，失败降级本地并记录日志 */
    public Map<String, Object> chat(Map<String, Object> body) {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        String sessionId = body.get("session_id") == null ? null : String.valueOf(body.get("session_id"));
        Object userId = body.get("user_id");
        String userRole = body.get("user_role") == null ? null : String.valueOf(body.get("user_role"));
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
                Map<String, Object> result = callFastApi(question, sessionId, userId, userRole, hits, aiService);
                long duration = System.currentTimeMillis() - start;
                saveLog(requestId, "QA", "SUCCESS", (int) duration, question,
                        String.valueOf(result.get("answer")).length(),
                        (String) result.get("model"), (String) result.get("source"), null);
                log.info("[AI-QA] requestId={} 用户={} 问题={} 来源={} 耗时={}ms 回答长度={}",
                        requestId, userId, truncate(question, 100), result.get("source"), duration,
                        String.valueOf(result.get("answer")).length());
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                saveLog(requestId, "QA", "FAILED", (int) duration, question, 0, "fastapi", null, e.getMessage());
                log.warn("FastAPI AI 服务调用失败，降级为本地知识库检索：{}", e.getMessage());
                log.info("[AI-QA] requestId={} 用户={} 问题={} 调用FastAPI失败耗时={}ms 错误={}",
                        requestId, userId, truncate(question, 100), duration, e.getMessage());
            }
        }

        // 3. 降级：本地知识库检索
        long duration = System.currentTimeMillis() - start;
        String answer = buildFallbackAnswer(question, hits);
        String source = hits.isEmpty() ? "未找到匹配知识" : hits.get(0).getTitle();
        saveLog(requestId, "QA", "FALLBACK", (int) duration, question, answer.length(), "knowledge-base", source, null);
        log.info("[AI-QA] requestId={} 用户={} 问题={} 来源=本地知识库 耗时={}ms 命中数={}",
                requestId, userId, truncate(question, 100), duration, hits.size());
        return Map.of("answer", answer, "source", source, "references", brief(hits), "fallback", true);
    }

    /** 调用 FastAPI 的 /api/ai/chat 接口，携带本地检索知识、用户画像、工单与知识库目录 */
    private Map<String, Object> callFastApi(String question, String sessionId, Object userId, String userRole,
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
        // 注入用户身份、近期工单与知识库全量目录，供模型做个性化回答
        contextService.applyContext(payload, userId, userRole);

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
        // 引用以 AI 服务的向量检索结果为准，兼容旧版（无 references 时用本地命中）
        List<Map<String, Object>> references = parseReferences(root);
        boolean grounded = root.has("grounded") ? root.path("grounded").asBoolean() : !references.isEmpty();
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
        result.put("references", references.isEmpty() ? brief(hits) : references);
        result.put("grounded", grounded);
        return result;
    }

    /** 解析 AI 服务返回的引用列表（向量检索命中的知识文章与相似度） */
    private List<Map<String, Object>> parseReferences(JsonNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode arr = node.path("references");
        if (!arr.isArray()) {
            return list;
        }
        for (JsonNode item : arr) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.path("id").asLong());
            m.put("title", item.path("title").asText());
            m.put("categoryName", item.path("category_name").asText(null));
            m.put("summary", item.path("summary").asText(null));
            m.put("score", item.path("score").asDouble(0));
            list.add(m);
        }
        return list;
    }

    /** 生成工单处置建议步骤：查工单与相关知识后转发给 AI 服务，失败时用知识文章兜底 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> suggestSteps(Long ticketId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT t.title, t.description, c.name AS category_name FROM ticket t " +
                    "LEFT JOIN ticket_category c ON c.id = t.category_id WHERE t.id = ?", ticketId);
        } catch (Exception e) {
            throw new com.zyyqq.desk.common.BizException("工单不存在");
        }
        String title = row.get("title") == null ? "" : String.valueOf(row.get("title"));
        String description = row.get("description") == null ? "" : String.valueOf(row.get("description"));
        String category = row.get("category_name") == null ? "" : String.valueOf(row.get("category_name"));

        // 用标题检索相关知识作为建议依据（检索不到时退回分类名）
        List<KnowledgeArticle> hits = articleRepository.search(title.isBlank() ? category : title, 3);
        if (hits.isEmpty() && !category.isBlank()) {
            hits = articleRepository.search(category, 3);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", truncate(title, 200));
        payload.put("description", truncate(description, 1500));
        payload.put("category", category);
        payload.put("knowledge", knowledgePayload(hits));
        try {
            Object result = proxy("POST", "/api/ai/suggest-steps", payload);
            Map<String, Object> map = result instanceof Map ? (Map<String, Object>) result : new HashMap<>();
            log.info("[AI-Steps] 工单={} 生成建议 {} 条", ticketId,
                    map.get("steps") instanceof List ? ((List<?>) map.get("steps")).size() : 0);
            return map;
        } catch (Exception e) {
            // AI 服务不可用时用知识库兜底，保证详情页右栏不空
            log.warn("AI 排查建议生成失败，使用知识库兜底：{}", e.getMessage());
            List<Map<String, Object>> steps = new ArrayList<>();
            int order = 1;
            for (KnowledgeArticle a : hits) {
                Map<String, Object> step = new HashMap<>();
                step.put("title", "参考《" + a.getTitle() + "》处理");
                step.put("detail", a.getSummary() == null || a.getSummary().isBlank()
                        ? "打开该文章查看完整排查步骤" : a.getSummary());
                step.put("order", order++);
                steps.add(step);
            }
            if (steps.isEmpty()) {
                Map<String, Object> step = new HashMap<>();
                step.put("title", "先补充复现信息");
                step.put("detail", "知识库暂无匹配方案，建议向客户确认复现步骤、环境与报错截图");
                step.put("order", 1);
                steps.add(step);
            }
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("steps", steps);
            fallback.put("fallback", true);
            fallback.put("source", "知识库");
            return fallback;
        }
    }

    /** 通用代理：把会话管理等请求转发给 FastAPI AI 服务 */
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
            log.info("[AI-{}] 代理调用成功 路径={} 响应长度={}", method.toUpperCase(), pathWithQuery,
                    resp.body().length());
            return objectMapper.readValue(resp.body(), Object.class);
        } catch (com.zyyqq.desk.common.BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 服务代理调用失败({} {})：{}", method, pathWithQuery, e.getMessage());
            throw new com.zyyqq.desk.common.BizException("AI 服务暂时不可用，请确认已启动 ai-service");
        }
    }

    /** 把检索到的知识文章组装为传给 AI 服务的 payload */
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

    /** 模糊检索：把问题切分为 2 字短语按命中次数评分取最相关文章 */
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

    /** 用本地知识库命中的文章拼装降级回答 */
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

    /** 把命中文章精简为引用摘要列表 */
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

    /** 保存 AI 调用日志（失败不影响主流程） */
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

    /** 流式问答：调用 FastAPI 流式端点逐段回调 delta，完成回调 done */
    public void chatStream(Map<String, Object> body, StreamCallback callback) throws Exception {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        String sessionId = body.get("session_id") == null ? null : String.valueOf(body.get("session_id"));
        Object userId = body.get("user_id");
        String userRole = body.get("user_role") == null ? null : String.valueOf(body.get("user_role"));
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();

        if (question == null || question.isBlank()) {
            callback.onDelta("请输入您的问题");
            callback.onDone("无", true, null, null, (int) (System.currentTimeMillis() - start), List.of(), false);
            return;
        }

        // 本地知识库检索作为上下文
        List<KnowledgeArticle> hits = articleRepository.search(question.trim(), 5);
        if (hits.isEmpty()) {
            hits = fuzzySearch(question.trim());
        }

        AppProperties.AiService aiService = props.getAiService();
        if (aiService.getBaseUrl() == null || aiService.getBaseUrl().isBlank()) {
            // 无 AI 服务，直接本地降级（一次性返回）
            String answer = buildFallbackAnswer(question, hits);
            String source = hits.isEmpty() ? "未找到可靠知识依据" : hits.get(0).getTitle();
            callback.onDelta(answer);
            callback.onDone(source, true, null, null, (int) (System.currentTimeMillis() - start), brief(hits), !hits.isEmpty());
            saveLog(requestId, "QA", "FALLBACK", (int) (System.currentTimeMillis() - start), question,
                    answer.length(), "knowledge-base", source, null);
            log.info("[AI-QA-流式] requestId={} 用户={} 问题={} 本地降级 命中={}",
                    requestId, userId, truncate(question, 100), hits.size());
            return;
        }

        // 组装 payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question);
        payload.put("knowledge", knowledgePayload(hits));
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("session_id", sessionId);
        }
        if (userId != null) {
            payload.put("user_id", userId);
        }
        // 注入用户身份、近期工单与知识库全量目录（流式线程内读取，故角色由 body 传入）
        contextService.applyContext(payload, userId, userRole);

        String url = aiService.getBaseUrl().replaceAll("/+$", "") + "/api/ai/chat/stream";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(aiService.getTimeoutSeconds() + 30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        StringBuilder answerBuf = new StringBuilder();
        String[] sourceRef = {"模型生成"};
        boolean[] fallbackRef = {false};
        String[] modelRef = {null};
        String[] sidRef = {null};
        Integer[] durationRef = {null};
        List<Map<String, Object>> refsRef = new ArrayList<>();
        boolean[] groundedRef = {false};

        try {
            HttpResponse<java.io.InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("AI 服务返回 " + resp.statusCode());
            }
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String json = line.substring(6).trim();
                    if (json.isEmpty()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(json);
                    String event = node.path("event").asText();
                    if ("delta".equals(event)) {
                        String piece = node.path("data").asText();
                        if (piece != null && !piece.isEmpty()) {
                            answerBuf.append(piece);
                            callback.onDelta(piece);
                        }
                    } else if ("done".equals(event)) {
                        sourceRef[0] = node.path("source").asText("模型生成");
                        fallbackRef[0] = node.path("fallback").asBoolean(false);
                        modelRef[0] = node.path("model").asText(null);
                        sidRef[0] = node.path("session_id").asText(null);
                        durationRef[0] = node.path("duration_ms").asInt(0);
                        // 引用与可信判定随 done 事件下发，供前端展示「回答依据」
                        List<Map<String, Object>> refs = parseReferences(node);
                        refsRef.clear();
                        refsRef.addAll(refs);
                        groundedRef[0] = node.has("grounded")
                                ? node.path("grounded").asBoolean() : !refs.isEmpty();
                    }
                }
            }
        } catch (Exception e) {
            // 流式失败，降级本地
            String answer = buildFallbackAnswer(question, hits);
            String source = hits.isEmpty() ? "未找到可靠知识依据" : hits.get(0).getTitle();
            answerBuf.setLength(0);
            answerBuf.append(answer);
            sourceRef[0] = source;
            fallbackRef[0] = true;
            refsRef.clear();
            refsRef.addAll(brief(hits));
            groundedRef[0] = !hits.isEmpty();
            callback.onDelta(answer);
            log.warn("流式调用失败，降级本地：{}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        callback.onDone(sourceRef[0], fallbackRef[0], modelRef[0], sidRef[0], (int) duration,
                refsRef, groundedRef[0]);
        saveLog(requestId, "QA", fallbackRef[0] ? "FALLBACK" : "SUCCESS", (int) duration, question,
                answerBuf.length(), fallbackRef[0] ? "knowledge-base" : modelRef[0], sourceRef[0], null);
        log.info("[AI-QA-流式] requestId={} 用户={} 问题={} 来源={} 耗时={}ms 回答长度={}",
                requestId, userId, truncate(question, 100), sourceRef[0], duration, answerBuf.length());
    }

    /** 流式回调接口：onDelta 逐段返回文本，onDone 返回完成元信息（引用与可信判定） */
    @FunctionalInterface
    public interface StreamCallback {
        void onDelta(String delta) throws Exception;

        default void onDone(String source, boolean fallback, String model, String sessionId, int durationMs,
                            java.util.List<Map<String, Object>> references, boolean grounded) throws Exception {
        }
    }

    /** 截断字符串到指定长度（避免日志/存库过长） */
    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n);
    }
}
