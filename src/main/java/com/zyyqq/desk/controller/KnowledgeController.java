package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.entity.KnowledgeCategory;
import com.zyyqq.desk.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<KnowledgeCategory>> categories() {
        return ApiResponse.ok(knowledgeService.categories());
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(knowledgeService.list(page, pageSize, keyword));
    }

    @GetMapping("/hot")
    public ApiResponse<List<KnowledgeArticle>> hot(@RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(knowledgeService.hot(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeArticle> detail(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.detail(id));
    }

    @PostMapping
    public ApiResponse<KnowledgeArticle> save(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(knowledgeService.save(body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok();
    }
}
