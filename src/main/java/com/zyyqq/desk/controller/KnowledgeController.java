package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.entity.KnowledgeCategory;
import com.zyyqq.desk.service.KnowledgeService;
import com.zyyqq.desk.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 知识库接口：分类、文章增删改查、排序与收藏、热门与详情 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 知识分类列表 */
    @GetMapping("/categories")
    public ApiResponse<List<KnowledgeCategory>> categories() {
        return ApiResponse.ok(knowledgeService.categories());
    }

    /** 文章分页列表（支持关键字、排序与只看我的收藏） */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(defaultValue = "newest") String sort,
                               @RequestParam(defaultValue = "false") boolean onlyFavorite) {
        return ApiResponse.ok(knowledgeService.list(page, pageSize, keyword, sort, onlyFavorite));
    }

    /** 热门文章 */
    @GetMapping("/hot")
    public ApiResponse<List<KnowledgeArticle>> hot(@RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(knowledgeService.hot(limit));
    }

    /** 文章详情 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.detail(id));
    }

    /** 收藏文章（任意身份均可） */
    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.favorite(id));
    }

    /** 取消收藏 */
    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> unfavorite(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.unfavorite(id));
    }

    /** 新增或更新文章（仅主管与管理员可维护知识库，客服与客户只读） */
    @PostMapping
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public ApiResponse<KnowledgeArticle> save(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(knowledgeService.save(body));
    }

    /** 删除文章（仅主管与管理员可维护知识库，客服与客户只读） */
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok();
    }
}
