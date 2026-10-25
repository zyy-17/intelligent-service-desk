package com.zyyqq.desk.controller;

import com.zyyqq.desk.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台全局搜索：一次关键字同时检索工单/客户/知识库 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final JdbcTemplate jdbc;

    public SearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 全局搜索入口 */
    @GetMapping
    public ApiResponse<Map<String, Object>> search(@RequestParam("q") String q) {
        String kw = q == null ? "" : q.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tickets", searchTickets(kw));
        result.put("customers", searchCustomers(kw));
        result.put("articles", searchArticles(kw));
        return ApiResponse.ok(result);
    }

    /** 按关键字检索工单 */
    private List<Map<String, Object>> searchTickets(String kw) {
        String like = "%" + kw + "%";
        return jdbc.queryForList(
                "SELECT t.id, t.ticket_no, t.title, t.priority, t.status, c.display_name AS customerName " +
                "FROM ticket t LEFT JOIN sys_user c ON t.customer_id = c.id " +
                "WHERE t.ticket_no LIKE ? OR t.title LIKE ? ORDER BY t.created_at DESC LIMIT 6", like, like);
    }

    /** 按关键字检索客户 */
    private List<Map<String, Object>> searchCustomers(String kw) {
        String like = "%" + kw + "%";
        return jdbc.queryForList(
                "SELECT id, username, display_name, company, email " +
                "FROM sys_user WHERE role = 'CUSTOMER' AND status = 1 " +
                "AND (username LIKE ? OR display_name LIKE ? OR company LIKE ?) LIMIT 6", like, like, like);
    }

    /** 按关键字检索知识文章 */
    private List<Map<String, Object>> searchArticles(String kw) {
        String like = "%" + kw + "%";
        return jdbc.queryForList(
                "SELECT id, title, view_count FROM knowledge_article " +
                "WHERE status = 'PUBLISHED' AND title LIKE ? ORDER BY view_count DESC LIMIT 6", like);
    }
}
