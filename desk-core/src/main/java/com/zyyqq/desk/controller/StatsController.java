package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 统计接口：全站概况、个人概览、工单趋势、团队绩效 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;
    private final com.zyyqq.desk.service.KnowledgeService knowledgeService;

    public StatsController(StatsService statsService, com.zyyqq.desk.service.KnowledgeService knowledgeService) {
        this.statsService = statsService;
        this.knowledgeService = knowledgeService;
    }

    /** 全站服务概况 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    /** 我的概览（按角色差异化） */
    @GetMapping("/my-overview")
    public ApiResponse<Map<String, Object>> myOverview() {
        return ApiResponse.ok(statsService.myOverview());
    }

    /** 工单趋势 */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(statsService.trend(days));
    }

    /** 团队绩效看板（主管/管理员） */
    @GetMapping("/team-performance")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public ApiResponse<List<Map<String, Object>>> teamPerformance() {
        return ApiResponse.ok(statsService.teamPerformance());
    }

    /** 知识库表现（主管/管理员）：浏览与收藏汇总 + Top 文章 */
    @GetMapping("/knowledge")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public ApiResponse<Map<String, Object>> knowledge(@RequestParam(defaultValue = "8") int top) {
        return ApiResponse.ok(knowledgeService.overview(top));
    }
}
