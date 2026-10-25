package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.security.AuthContext;
import com.zyyqq.desk.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        // 附带当前登录用户，供 AI 服务做多轮会话归属
        body.put("user_id", AuthContext.require().id());
        return ApiResponse.ok(aiService.chat(body));
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
