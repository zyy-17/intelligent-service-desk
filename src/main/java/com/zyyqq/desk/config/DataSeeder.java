package com.zyyqq.desk.config;

import com.zyyqq.desk.security.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 演示数据初始化：仅在数据库为空时填充，保证可重复启动且不污染已有数据。
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        if (count != null && count > 0) {
            log.info("已存在用户数据，跳过演示数据初始化");
            return;
        }
        seed();
        log.info("演示数据初始化完成");
    }

    private void seed() {
        // ===== 用户 =====
        List<Long> adminIds = insertUsers();
        long adminId = adminIds.get(0);
        long supId = adminIds.get(1);

        // ===== 分类 =====
        long catAccount = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "账户问题", "#6366f1", 8, 1);
        long catProduct = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "产品咨询", "#22c55e", 24, 2);
        long catTech = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "技术故障", "#f59e0b", 12, 3);
        long catBilling = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "账单与支付", "#ef4444", 6, 4);
        long catSuggestion = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "意见建议", "#06b6d4", 48, 5);
        long catOther = insertAndGetId("INSERT INTO ticket_category(name,color,sla_hours,sort) VALUES(?,?,?,?)",
                "其他", "#a855f7", 24, 6);
        long[] cats = {catAccount, catProduct, catTech, catBilling, catSuggestion, catOther};

        // ===== 知识分类 =====
        long kcGuide = insertAndGetId("INSERT INTO knowledge_category(name,sort) VALUES(?,?)", "使用指南", 1);
        long kcFaq = insertAndGetId("INSERT INTO knowledge_category(name,sort) VALUES(?,?)", "常见问题", 2);
        long kcAccount = insertAndGetId("INSERT INTO knowledge_category(name,sort) VALUES(?,?)", "账号安全", 3);
        long kcBilling = insertAndGetId("INSERT INTO knowledge_category(name,sort) VALUES(?,?)", "账单与退款", 4);
        long[] kcats = {kcGuide, kcFaq, kcAccount, kcBilling};

        seedKnowledge(kcats, adminId);

        seedTickets(adminId, supId, cats);

        seedNotification(adminId, supId);
    }

    private List<Long> insertUsers() {
        List<Long> ids = new ArrayList<>();
        ids.add(insertUser("admin", "管理员", "ADMIN", "13800000000", null, "#6366f1"));
        ids.add(insertUser("supervisor", "王主管", "SUPERVISOR", "13800000001", null, "#f59e0b"));
        String[] agents = {"张伟", "李娜", "刘强", "陈静", "杨洋", "赵敏", "孙磊", "周婷"};
        for (int i = 0; i < agents.length; i++) {
            insertUser("agent" + (i + 1), agents[i], "AGENT", "1380001000" + i, "客服部", null);
        }
        String[] customers = {"陈先生", "林女士", "黄同学", "徐女士", "马先生", "朱小姐", "郑先生", "何女士", "高先生", "罗女士"};
        for (int i = 0; i < customers.length; i++) {
            insertUser("customer" + (i + 1), customers[i], "CUSTOMER", "1390001000" + i, null, null);
        }
        // 批量客户（用于工单多样性）
        String[] batchCustomers = {"吴先生", "郭女士", "邓先生", "叶女士", "沈先生", "彭女士", "谢先生", "冯女士", "曹先生", "曾女士"};
        for (int i = 0; i < batchCustomers.length; i++) {
            insertUser("cus" + (i + 1), batchCustomers[i], "CUSTOMER", "1370001000" + i, null, null);
        }
        return ids;
    }

    private long insertUser(String username, String displayName, String role, String phone, String dept, String color) {
        return insertAndGetId(
                "INSERT INTO sys_user(username,password_hash,display_name,role,phone,department,avatar_color,status) " +
                        "VALUES(?,?,?,?,?,?,?,1)",
                username, PasswordUtil.hash("123456"), displayName, role, phone, dept, color);
    }

    private long insertAndGetId(String sql, Object... args) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private void seedKnowledge(long[] kcats, long adminId) {
        Object[][] articles = {
                {"如何重置登录密码", "忘记密码时可通过注册邮箱或手机号自助重置，本文说明操作步骤。",
                        "**操作步骤**\n\n1. 打开登录页，点击「忘记密码」。\n2. 输入注册时的手机号或邮箱，获取验证码。\n3. 验证通过后设置新密码（至少 8 位，含字母和数字）。\n4. 使用新密码登录。\n\n> 若无法收到验证码，请联系人工客服协助处理。",
                        "密码,重置,登录,忘记", kcats[2]},
                {"如何申请退款", "满足条件的订单可在 7 天内申请退款，退款原路返回。",
                        "**退款条件**\n\n- 订单支付后 7 天内；\n- 服务未使用或未激活；\n- 无违反服务条款的行为。\n\n**申请方式**\n\n在「账单中心 - 订单详情」点击「申请退款」，填写原因后提交，1-3 个工作日审核。",
                        "退款,申请,订单,退费", kcats[3]},
                {"如何导出数据报表", "支持按时间范围导出 CSV 与 Excel 格式的数据报表。",
                        "**导出步骤**\n\n1. 进入「数据中心 - 报表」。\n2. 选择时间范围与指标维度。\n3. 点击「导出」，选择格式。\n\n> 单次导出上限 10 万条记录。",
                        "导出,报表,数据,CSV,Excel", kcats[0]},
                {"如何绑定手机号", "绑定手机号后可增强账号安全性并用于找回密码。",
                        "**绑定步骤**\n\n1. 进入「个人中心 - 安全设置」。\n2. 点击「绑定手机号」。\n3. 输入手机号并完成短信验证。",
                        "手机号,绑定,安全", kcats[2]},
                {"如何开启二次验证", "开启两步验证可为账号增加额外保护层。",
                        "**开启方式**\n\n1. 安全设置中选择「两步验证」。\n2. 下载身份验证器 App 并扫码。\n3. 输入动态码完成绑定。",
                        "二次验证,两步验证,安全,2FA", kcats[2]},
                {"套餐如何升级", "支持随时升级套餐，费用按剩余天数折算。",
                        "**升级方式**\n\n进入「账户中心 - 套餐管理」，选择目标套餐后确认，差价按天折算，立即生效。",
                        "套餐,升级,续费", kcats[3]},
                {"为什么登录一直失败", "登录失败常见原因及排查方法。",
                        "**常见原因**\n\n- 账号或密码错误；\n- 账号被禁用；\n- 网络或缓存问题。\n\n**解决**\n\n核对账号密码 → 清除浏览器缓存 → 仍失败则联系客服。",
                        "登录,失败,排查", kcats[1]},
                {"如何联系人工客服", "多种渠道联系人工客服的方式。",
                        "**渠道**\n\n- 在线提交工单；\n- 客服电话 400-800-8888；\n- 邮件 support@example.com。\n\n工单平均响应时间小于 2 小时。",
                        "人工客服,联系,电话,工单", kcats[1]},
                {"发票如何申请", "支持开具电子增值税普通发票与专用发票。",
                        "**申请方式**\n\n「账单中心 - 发票管理」填写抬头与税号后提交，电子发票 1 个工作日内开具。",
                        "发票,开票,税号", kcats[3]},
                {"API 如何接入", "开发者可通过开放 API 接入平台能力。",
                        "**接入步骤**\n\n1. 在「开发者中心」创建应用获取 AppKey。\n2. 阅读接口文档。\n3. 使用签名方式调用接口。",
                        "API,接入,开发,AppKey", kcats[0]},
        };
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Random rnd = new Random(42);
        for (Object[] a : articles) {
            String published = LocalDateTime.now().minusDays(rnd.nextInt(60)).format(fmt);
            int views = 50 + rnd.nextInt(3000);
            jdbc.update("INSERT INTO knowledge_article(title,summary,body,category_id,keywords,status,author_id,view_count,published_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?)",
                    a[0], a[1], a[2], a[4], a[3], "PUBLISHED", adminId, views, published);
        }
    }

    private void seedTickets(long adminId, long supId, long[] cats) {
        // 客服与客户 id
        List<Long> agentIds = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE role='AGENT' ORDER BY id", Long.class);
        List<Long> customerIds = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE role='CUSTOMER' ORDER BY id", Long.class);

        String[] titles = {
                "无法登录系统，提示账号或密码错误", "账户余额显示异常", "需要导出本季度数据报表",
                "页面加载缓慢，影响使用", "申请退款，订单未生效", "如何升级到企业版套餐",
                "API 调用返回 401 错误", "想了解产品新功能", "发票抬头需要修改",
                "二次验证绑定失败", "支付成功后订单状态未更新", "希望增加批量导入功能",
                "手机收不到验证码", "系统偶发闪退", "数据统计与实际情况不符",
                "账号疑似被盗", "建议增加夜间模式", "合同电子章无法生成",
                "咨询续费优惠政策", "告警通知未收到", "文件上传失败",
                "权限分配不合理", "报表导出中文乱码", "接口响应超时",
                "希望支持微信登录", "客服响应速度慢", "登录后频繁掉线",
                "退款进度查询", "账单金额核对", "如何解绑旧设备",
        };
        String[] priorities = {"URGENT", "HIGH", "MEDIUM", "LOW"};
        double[] priorityWeight = {0.10, 0.25, 0.50, 0.15};
        String[] statuses = {"PENDING", "ASSIGNED", "PROCESSING", "WAITING", "RESOLVED", "CLOSED"};
        double[] statusWeight = {0.10, 0.15, 0.30, 0.05, 0.20, 0.20};
        String[] sources = {"WEB", "PHONE", "EMAIL", "WECHAT"};

        Random rnd = new Random(2026);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int total = 1286;
        int pendingCount = 0, overdueCount = 0;

        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String ticketNo = "ISD" + (20260000 + i);
            String title = titles[rnd.nextInt(titles.length)];
            String desc = "客户反馈：" + title + "。\n期望尽快处理，谢谢。";
            Long customerId = customerIds.get(rnd.nextInt(customerIds.size()));
            int catIdx = rnd.nextInt(cats.length);
            String priority = weighted(priorities, priorityWeight, rnd);
            String status = weighted(statuses, statusWeight, rnd);
            String source = sources[rnd.nextInt(sources.length)];

            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(rnd.nextInt(60 * 24 * 30)); // 近30天
            LocalDateTime firstResponse = null;
            LocalDateTime resolvedAt = null;
            LocalDateTime closedAt = null;
            LocalDateTime slaResolveAt = null;
            LocalDateTime slaResponseAt = null;
            Long assigneeId = null;
            Integer satisfaction = null;
            String solution = null;
            int reopenCount = 0;

            if (!status.equals("PENDING")) {
                assigneeId = agentIds.get(rnd.nextInt(agentIds.size()));
                slaResponseAt = createdAt.plusHours(2);
            }

            boolean overdue = false;
            if (status.equals("PENDING")) {
                slaResolveAt = createdAt.plusHours(8 + rnd.nextInt(16));
                if (createdAt.plusHours(8).isBefore(LocalDateTime.now())) {
                    overdue = true;
                }
            } else if (status.equals("ASSIGNED") || status.equals("PROCESSING") || status.equals("WAITING")) {
                slaResolveAt = createdAt.plusHours(8 + rnd.nextInt(40));
                firstResponse = createdAt.plusMinutes(5 + rnd.nextInt(180));
                if (status.equals("PROCESSING") && slaResolveAt.isBefore(LocalDateTime.now())) {
                    overdue = true;
                }
            } else { // RESOLVED / CLOSED
                slaResolveAt = createdAt.plusHours(8 + rnd.nextInt(40));
                firstResponse = createdAt.plusMinutes(5 + rnd.nextInt(180));
                resolvedAt = createdAt.plusHours(2 + rnd.nextInt(48));
                solution = "已协助客户完成处理：" + title + "。\n如仍有疑问欢迎随时联系。";
                satisfaction = 3 + rnd.nextInt(3); // 3-5
                if (status.equals("CLOSED")) {
                    closedAt = resolvedAt.plusHours(2 + rnd.nextInt(24));
                }
                if (rnd.nextInt(10) < 2) {
                    reopenCount = 1;
                }
            }

            if (overdue) overdueCount++;
            if (status.equals("PENDING")) pendingCount++;

            String feedback = null;
            if (satisfaction != null) {
                feedback = rnd.nextInt(2) == 0 ? "处理得很及时，非常满意。" : "问题已解决，谢谢。";
            }

            batch.add(new Object[]{ticketNo, title, desc, customerId, assigneeId, (long) cats[catIdx],
                    priority, status, source, solution, satisfaction, feedback,
                    fmt(slaResponseAt, fmt), fmt(slaResolveAt, fmt), fmt(firstResponse, fmt),
                    fmt(resolvedAt, fmt), fmt(closedAt, fmt), reopenCount, fmt(createdAt, fmt)});
        }

        jdbc.batchUpdate(
                "INSERT INTO ticket(ticket_no,title,description,customer_id,assignee_id,category_id," +
                        "priority,status,source,solution,satisfaction,feedback,sla_response_at,sla_resolve_at," +
                        "first_response_at,resolved_at,closed_at,reopen_count,created_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch);
        log.info("工单演示数据已生成 {} 条，待分配 {} 条，超时 {} 条", total, pendingCount, overdueCount);

        // 为部分工单补充消息与历史
        seedTicketMessages(agentIds, customerIds, rnd, fmt);
    }

    private void seedTicketMessages(List<Long> agentIds, List<Long> customerIds, Random rnd, DateTimeFormatter fmt) {
        List<Long> ticketIds = jdbc.queryForList("SELECT id FROM ticket ORDER BY id LIMIT 200", Long.class);
        List<Object[]> msgs = new ArrayList<>();
        List<Object[]> histories = new ArrayList<>();
        String[] customerMsgs = {"请问这个问题什么时候能处理？", "好的，我试一下。", "还有补充信息，请看附件。", "谢谢！"};
        String[] agentMsgs = {"已收到您的反馈，正在为您处理。", "请问您方便提供一下截图吗？", "问题已定位，正在修复。", "已为您解决，请确认。"};
        String[] internalNotes = {"客户为老客户，优先处理。", "已电话沟通确认细节。", "需要升级到二线支持。"};

        for (Long tid : ticketIds) {
            int n = 1 + rnd.nextInt(3);
            for (int j = 0; j < n; j++) {
                boolean fromAgent = rnd.nextBoolean();
                Long sender = fromAgent ? agentIds.get(rnd.nextInt(agentIds.size())) : customerIds.get(rnd.nextInt(customerIds.size()));
                String content = fromAgent ? agentMsgs[rnd.nextInt(agentMsgs.length)] : customerMsgs[rnd.nextInt(customerMsgs.length)];
                msgs.add(new Object[]{tid, sender, "PUBLIC", content, LocalDateTime.now().minusHours(rnd.nextInt(72)).format(fmt)});
            }
            if (rnd.nextInt(10) < 4) {
                Long sender = agentIds.get(rnd.nextInt(agentIds.size()));
                msgs.add(new Object[]{tid, sender, "INTERNAL", internalNotes[rnd.nextInt(internalNotes.length)],
                        LocalDateTime.now().minusHours(rnd.nextInt(72)).format(fmt)});
            }
            histories.add(new Object[]{tid, customerIds.get(rnd.nextInt(customerIds.size())), "CREATE", null, "PENDING",
                    "客户创建工单", LocalDateTime.now().minusHours(rnd.nextInt(72)).format(fmt)});
        }
        jdbc.batchUpdate("INSERT INTO ticket_message(ticket_id,sender_id,message_type,content,created_at) VALUES(?,?,?,?,?)", msgs);
        jdbc.batchUpdate("INSERT INTO ticket_history(ticket_id,operator_id,action,old_value,new_value,remark,created_at) VALUES(?,?,?,?,?,?,?)", histories);
    }

    private void seedNotification(long adminId, long supId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = LocalDateTime.now().format(fmt);
        jdbc.update("INSERT INTO notification(recipient_id,type,title,content,is_read,created_at) VALUES(?,?,?,?,?,?)",
                adminId, "SYSTEM", "系统上线通知", "智能客服与工单管理平台已成功部署上线。", 0, now);
        jdbc.update("INSERT INTO notification(recipient_id,type,title,content,is_read,created_at) VALUES(?,?,?,?,?,?)",
                supId, "TICKET", "有待分配工单", "当前存在待分配工单，请及时处理。", 0, now);
    }

    private String fmt(LocalDateTime t, DateTimeFormatter fmt) {
        return t == null ? null : t.format(fmt);
    }

    private String weighted(String[] items, double[] weights, Random rnd) {
        double r = rnd.nextDouble();
        double acc = 0;
        for (int i = 0; i < items.length; i++) {
            acc += weights[i];
            if (r < acc) {
                return items[i];
            }
        }
        return items[items.length - 1];
    }
}
