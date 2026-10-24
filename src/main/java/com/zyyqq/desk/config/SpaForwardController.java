package com.zyyqq.desk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 前端路由回退：/login、/dashboard 等前端路由直接访问时返回 index.html。
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {"/login", "/dashboard", "/tickets", "/customers",
            "/knowledge", "/chat", "/stats", "/system"})
    public String forward() {
        return "forward:/index.html";
    }
}
