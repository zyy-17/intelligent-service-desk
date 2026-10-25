package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.security.AuthContext;
import com.zyyqq.desk.service.AiService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** 智能问答接口：流式/非流式问答、摘要、分类、回复草稿、会话管理 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 非流式问答 */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        // 附带当前登录用户与角色，供 AI 服务读取用户身份、工单与会话归属
        AuthContext.UserInfo u = AuthContext.require();
        body.put("user_id", u.id());
        body.put("user_role", u.role());
        return ApiResponse.ok(aiService.chat(body));
    }

    /** 个性化欢迎语：突出 AI 名字，结合当前用户身份与工单概况 */
    @GetMapping("/greeting")
    public ApiResponse<Map<String, Object>> greeting() {
        AuthContext.UserInfo u = AuthContext.require();
        return ApiResponse.ok(aiService.greeting(u.id(), u.role(), u.displayName()));
    }

    /** 流式问答：SSE 逐段返回回答，含思考时间与回答耗时 */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        body.put("user_id", u.id());
        body.put("user_role", u.role());
        SseEmitter emitter = new SseEmitter(120_000L);
        new Thread(() -> {
            try {
                aiService.chatStream(body, new AiService.StreamCallback() {
                    @Override
                    public void onDelta(String delta) throws Exception {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    }

                    @Override
                    public void onDone(String source, boolean fallback, String model, String sessionId, int durationMs) throws Exception {
                        Map<String, Object> meta = new java.util.HashMap<>();
                        meta.put("source", source);
                        meta.put("fallback", fallback);
                        meta.put("model", model);
                        meta.put("session_id", sessionId);
                        meta.put("duration_ms", durationMs);
                        emitter.send(SseEmitter.event().name("done").data(meta));
                        emitter.complete();
                    }
                });
            } catch (Exception e) {
                try {
                    Map<String, Object> err = new java.util.HashMap<>();
                    err.put("error", e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(err));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        }).start();
        return emitter;
    }

    /** 工单摘要 */
    @PostMapping("/summarize")
    public ApiResponse<Object> summarize(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.proxy("POST", "/api/ai/summarize", body));
    }

    /** 分类推荐 */
    @PostMapping("/classify")
    public ApiResponse<Object> classify(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.proxy("POST", "/api/ai/classify", body));
    }

    /** 回复草稿 */
    @PostMapping("/draft")
    public ApiResponse<Object> draft(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.proxy("POST", "/api/ai/draft", body));
    }

    /** 新建会话 */
    @PostMapping("/sessions")
    public ApiResponse<Object> createSession(@RequestBody Map<String, Object> body) {
        body.put("user_id", AuthContext.require().id());
        return ApiResponse.ok(aiService.proxy("POST", "/api/ai/sessions", body));
    }

    /** 会话列表 */
    @GetMapping("/sessions")
    public ApiResponse<Object> listSessions(@RequestParam(defaultValue = "50") int limit) {
        long uid = AuthContext.require().id();
        return ApiResponse.ok(aiService.proxy("GET",
                "/api/ai/sessions?user_id=" + uid + "&limit=" + limit, null));
    }

    /** 会话详情（含历史消息） */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<Object> getSession(@PathVariable String sessionId) {
        long uid = AuthContext.require().id();
        return ApiResponse.ok(aiService.proxy("GET",
                "/api/ai/sessions/" + sessionId + "?user_id=" + uid, null));
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Object> deleteSession(@PathVariable String sessionId) {
        long uid = AuthContext.require().id();
        return ApiResponse.ok(aiService.proxy("DELETE",
                "/api/ai/sessions/" + sessionId + "?user_id=" + uid, null));
    }
}
