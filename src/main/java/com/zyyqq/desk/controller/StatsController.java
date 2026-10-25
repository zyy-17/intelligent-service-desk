package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    @GetMapping("/my-overview")
    public ApiResponse<Map<String, Object>> myOverview() {
        return ApiResponse.ok(statsService.myOverview());
    }

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(statsService.trend(days));
    }
}
