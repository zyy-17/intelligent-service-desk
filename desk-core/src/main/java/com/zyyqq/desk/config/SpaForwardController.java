package com.zyyqq.desk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** SPA 前端路由回退：直接访问前端路由时返回 index.html */
@Controller
public class SpaForwardController {

    /** 将前端路由统一转发到 index.html */
    @RequestMapping(value = {"/login", "/dashboard", "/tickets", "/customers",
            "/knowledge", "/chat", "/stats", "/system"})
    public String forward() {
        return "forward:/index.html";
    }
}
