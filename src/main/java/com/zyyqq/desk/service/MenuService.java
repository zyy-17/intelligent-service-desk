package com.zyyqq.desk.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 菜单与权限配置中心：根据角色拼装侧边栏菜单并由后端统一下发 */
@Service
public class MenuService {

    /** 全部菜单项定义（含可见角色），前端据此渲染侧边栏；path 带前导 / 与前端路由一致 */
    private static final List<Map<String, Object>> ALL_MENUS = List.of(
            menu("/dashboard", "工作台", "Odometer", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("/tickets", "工单管理", "Tickets", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("/customers", "客户管理", "User", "ADMIN", "SUPERVISOR"),
            menu("/knowledge", "知识库", "Collection", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("/chat", "智能问答", "ChatDotRound", "ADMIN", "SUPERVISOR", "AGENT", "CUSTOMER"),
            menu("/stats", "数据统计", "DataAnalysis", "ADMIN", "SUPERVISOR"),
            menu("/system", "系统管理", "Setting", "ADMIN"),
            menu("/profile", "个人中心", "UserFilled", "CUSTOMER")
    );

    /** 客户端菜单文案覆盖：把管理味词汇换成客户视角的措辞 */
    private static final Map<String, String> CUSTOMER_LABELS = Map.of(
            "/tickets", "我的工单",
            "/chat", "AI 智能助手"
    );

    /** 根据角色返回可见菜单列表 */
    public List<Map<String, Object>> menusFor(String role) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : ALL_MENUS) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) m.get("roles");
            if (roles.contains(role)) {
                String path = (String) m.get("path");
                Map<String, Object> item = new HashMap<>();
                item.put("path", path);
                // 客户角色按客户视角覆盖文案，其他角色沿用通用文案
                item.put("label", "CUSTOMER".equals(role)
                        ? CUSTOMER_LABELS.getOrDefault(path, (String) m.get("label"))
                        : m.get("label"));
                item.put("icon", m.get("icon"));
                result.add(item);
            }
        }
        return result;
    }

    /** 构造单个菜单项（path/label/icon/roles） */
    private static Map<String, Object> menu(String path, String label, String icon, String... roles) {
        Map<String, Object> m = new HashMap<>();
        m.put("path", path);
        m.put("label", label);
        m.put("icon", icon);
        m.put("roles", List.of(roles));
        return m;
    }
}
