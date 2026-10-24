package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(ticketService.list(page, pageSize, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ticketService.create(
                str(body.get("title")), str(body.get("description")),
                body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId"))),
                str(body.get("priority"))));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.reply(id, body.get("content"), body.get("messageType"));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.assign(id, body.get("assigneeId") == null ? null : Long.valueOf(String.valueOf(body.get("assigneeId"))));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        ticketService.accept(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.resolve(id, body.get("solution"));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.confirm(id, Boolean.parseBoolean(String.valueOf(body.getOrDefault("solved", false))),
                str(body.get("feedback")));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Void> close(@PathVariable Long id) {
        ticketService.close(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<Void> reopen(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.reopen(id, body.get("reason"));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/rate")
    public ApiResponse<Void> rate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ticketService.rate(id, Integer.parseInt(String.valueOf(body.get("rating"))), str(body.get("comment")));
        return ApiResponse.ok();
    }

    @GetMapping("/meta/agents")
    public ApiResponse<List<Map<String, Object>>> agents() {
        return ApiResponse.ok(ticketService.agents());
    }

    @GetMapping("/meta/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(ticketService.categories());
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
