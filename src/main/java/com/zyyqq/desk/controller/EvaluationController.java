package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.EvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 客户评价接口：评价概览与明细（仅管理员/客服主管） */
@RestController
@RequestMapping("/api/evaluations")
@RequireRole({"ADMIN", "SUPERVISOR"})
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 评价明细分页（支持星级、客服、时间与关键字筛选） */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return ApiResponse.ok(evaluationService.list(page, pageSize, rating, agentId, keyword, start, end));
    }

    /** 评价概览统计（顶部指标卡 + 星级分布 + 客服排行 + 趋势） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(evaluationService.stats());
    }

    /** 客服列表（评价筛选下拉） */
    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> agents() {
        return ApiResponse.ok(evaluationService.agents());
    }
}
