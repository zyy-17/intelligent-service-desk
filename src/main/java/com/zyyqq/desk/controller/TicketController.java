package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.security.AuthContext;
import com.zyyqq.desk.security.RequireRole;
import com.zyyqq.desk.service.RedisSupport;
import com.zyyqq.desk.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 工单接口：查询、创建、回复、分配、处理、确认、评价、催办等 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final RedisSupport redisSupport;

    public TicketController(TicketService ticketService, RedisSupport redisSupport) {
        this.ticketService = ticketService;
        this.redisSupport = redisSupport;
    }

    /** 工单分页列表 */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(ticketService.list(page, pageSize, status, keyword));
    }

    /** 工单详情 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.detail(id));
    }

    /** 工单大厅：查看非本人负责的全部工单（客服用于申请认领，主管/管理员用于审核） */
    @GetMapping("/hall")
    @RequireRole({"AGENT", "ADMIN", "SUPERVISOR"})
    public ApiResponse<PageResult<Map<String, Object>>> hall(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(ticketService.hall(page, pageSize, status, keyword));
    }

    /** 客服申请负责处理未分配工单（状态转为待审核） */
    @PostMapping("/{id}/apply")
    @RequireRole({"AGENT"})
    public ApiResponse<Void> apply(@PathVariable Long id) {
        ticketService.apply(id);
        return ApiResponse.ok();
    }

    /** 主管/管理员审核接单申请（通过则工单归申请人，驳回则退回待分配） */
    @PostMapping("/{id}/review")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public ApiResponse<Void> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object approve = body.get("approve");
        boolean pass = approve == null || Boolean.parseBoolean(String.valueOf(approve));
        ticketService.review(id, pass, body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        return ApiResponse.ok();
    }

    /** 创建工单（带幂等去重） */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // 工单重复提交控制：客户端重复点击/网络重试时凭唯一请求标识去重
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestKey = AuthContext.require().id() + ":" + idempotencyKey;
            if (!redisSupport.tryIdempotent("ticket:" + requestKey, java.time.Duration.ofMinutes(10))) {
                throw new BizException(409, "工单正在提交中，请勿重复提交");
            }
        }
        return ApiResponse.ok(ticketService.create(
                str(body.get("title")), str(body.get("description")),
                body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId"))),
                str(body.get("priority"))));
    }

    /** 回复工单 */
    @PostMapping("/{id}/messages")
    public ApiResponse<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.reply(id, body.get("content"), body.get("messageType"));
        return ApiResponse.ok();
    }

    /** 分配/转派工单 */
    @PostMapping("/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.assign(id, body.get("assigneeId") == null ? null : Long.valueOf(String.valueOf(body.get("assigneeId"))));
        return ApiResponse.ok();
    }

    /** 客服接单 */
    @PostMapping("/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        ticketService.accept(id);
        return ApiResponse.ok();
    }

    /** 提交解决方案 */
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.resolve(id, body.get("solution"));
        return ApiResponse.ok();
    }

    /** 客户确认解决方案 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.confirm(id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("solved", false))),
                str(body.get("feedback")));
        return ApiResponse.ok();
    }

    /** 关闭工单 */
    @PostMapping("/{id}/close")
    public ApiResponse<Void> close(@PathVariable Long id) {
        ticketService.close(id);
        return ApiResponse.ok();
    }

    /** 重新打开工单 */
    @PostMapping("/{id}/reopen")
    public ApiResponse<Void> reopen(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.reopen(id, body.get("reason"));
        return ApiResponse.ok();
    }

    /** 客户评价工单 */
    @PostMapping("/{id}/rate")
    public ApiResponse<Void> rate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.rate(id, Integer.parseInt(String.valueOf(body.get("rating"))), str(body.get("comment")));
        return ApiResponse.ok();
    }

    /** 客户催办 */
    @PostMapping("/{id}/urge")
    public ApiResponse<Void> urge(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.urge(id, body.get("reason"));
        return ApiResponse.ok();
    }

    /** 客服列表 */
    @GetMapping("/meta/agents")
    public ApiResponse<List<Map<String, Object>>> agents() {
        return ApiResponse.ok(ticketService.agents());
    }

    /** 工单分类列表 */
    @GetMapping("/meta/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(ticketService.categories());
    }

    /** 对象转字符串（null 安全） */
    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
