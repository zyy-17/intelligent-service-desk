package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.TeamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 团队工作台接口：负载概览、预警工单列表与批量指派（仅管理员/客服主管） */
@RestController
@RequestMapping("/api/team")
@RequireRole({"ADMIN", "SUPERVISOR"})
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    /** 概览：顶部指标 + 客服负载 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(teamService.overview());
    }

    /** 工单列表（type：unassigned / overdue / dueSoon） */
    @GetMapping("/tickets")
    public ApiResponse<List<Map<String, Object>>> tickets(@RequestParam(defaultValue = "unassigned") String type) {
        return ApiResponse.ok(teamService.tickets(type));
    }

    /** 批量指派工单给指定客服 */
    @PostMapping("/assign")
    public ApiResponse<Map<String, Object>> assign(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ticketIds");
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new BizException("请先选择工单");
        }
        List<Long> ids = rawList.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        Long assigneeId = body.get("assigneeId") == null ? null : Long.valueOf(String.valueOf(body.get("assigneeId")));
        return ApiResponse.ok(teamService.assign(ids, assigneeId));
    }
}
