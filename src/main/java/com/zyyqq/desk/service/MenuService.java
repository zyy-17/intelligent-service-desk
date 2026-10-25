package com.zyyqq.desk.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单与权限配置中心：根据角色拼装侧边栏菜单，后端统一下发。
 * 新增角色 / 调整菜单权限只需改这里，前端无需维护角色映射。
 */
@Service
public class MenuService {

    /** 全部菜单项定义（含可见角色），前端据此渲染侧边栏 */
    private static final List<Map<String, Object>> ALL_MENUS = List.of(
            menu("dashboard", "工作台", "Odometer", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("tickets", "工单管理", "Tickets", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("customers", "客户管理", "User", "ADMIN", "SUPERVISOR"),
            menu("knowledge", "知识库", "Collection", "ADMIN", "SUPERVISOR", "AGENT"),
            menu("chat", "智能问答", "ChatDotRound", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("stats", "数据统计", "DataAnalysis", "ADMIN", "SUPERVISOR"),
            menu("system", "系统管理", "Setting", "ADMIN")
    );

    /**
     * 根据角色返回可见菜单列表。
     */
    public List<Map<String, Object>> menusFor(String role) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : ALL_MENUS) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) m.get("roles");
            if (roles.contains(role)) {
                Map<String, Object> item = new HashMap<>();
                item.put("path", m.get("path"));
                item.put("label", m.get("label"));
                item.put("icon", m.get("icon"));
                result.add(item);
            }
        }
        return result;
    }

    private static Map<String, Object> menu(String path, String label, String icon, String... roles) {
        Map<String, Object> m = new HashMap<>();
        m.put("path", path);
        m.put("label", label);
        m.put("icon", icon);
        m.put("roles", List.of(roles));
        return m;
    }
}
