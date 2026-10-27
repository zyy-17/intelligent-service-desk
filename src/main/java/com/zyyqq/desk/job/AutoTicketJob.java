package com.zyyqq.desk.job;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示用定时任务：每天按配置时间自动创建工单，并按比例模拟客服处理到「已解决」。
 * 默认关闭，需在 application.yml 设置 app.demo.auto-ticket.enabled=true 才会注册本 Bean。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.demo.auto-ticket", name = "enabled", havingValue = "true")
public class AutoTicketJob {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public AutoTicketJob(JdbcTemplate jdbc, TransactionTemplate tx, AppProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    /** 每天定时入口：生成一批工单，异常只记录日志不中断服务 */
    @Scheduled(cron = "${app.demo.auto-ticket.cron:0 0 9 * * ?}")
    public void dailyRun() {
        try {
            Map<String, Object> r = generate(props.getDemo().getAutoTicket().getCount());
            log.info("定时造单完成：新建 {} 条，自动解决 {} 条，待分配 {} 条",
                    r.get("created"), r.get("resolved"), r.get("pending"));
        } catch (Exception e) {
            log.error("定时造单失败：{}", e.getMessage(), e);
        }
    }

    /** 生成指定数量的工单，按配置比例模拟客服处理，返回本次统计（供定时任务与手动触发共用） */
    public Map<String, Object> generate(int count) {
        int n = Math.min(Math.max(count, 1), 50);
        List<Long> customers = ids("SELECT id FROM sys_user WHERE role = 'CUSTOMER' AND status = 1 ORDER BY id");
        List<Long> agents = ids("SELECT id FROM sys_user WHERE role = 'AGENT' AND status = 1 ORDER BY id");
        List<Map<String, Object>> categories = jdbc.queryForList(
                "SELECT id, name, sla_hours FROM ticket_category WHERE status = 1");
        if (customers.isEmpty()) {
            throw new BizException("系统中没有可用的客户账号，无法自动造单");
        }
        int percent = Math.min(Math.max(props.getDemo().getAutoTicket().getResolvePercent(), 0), 100);

        List<String> ticketNos = new ArrayList<>();
        int resolved = 0;
        for (int i = 0; i < n; i++) {
            // 每条工单独立事务，单条失败不影响其余
            boolean autoResolve = !agents.isEmpty() && ThreadLocalRandom.current().nextInt(100) < percent;
            // lambda 只能捕获 effectively final 变量，循环下标单独拷贝一份
            final int idx = i;
            try {
                String no = tx.execute(status ->
                        createOne(idx, customers, agents, categories, autoResolve));
                if (no != null) {
                    ticketNos.add(no);
                    if (autoResolve) {
                        resolved++;
                    }
                }
            } catch (Exception e) {
                log.warn("自动造单第 {} 条失败：{}", i + 1, e.getMessage());
            }
        }

        Map<String, Object> r = new HashMap<>();
        r.put("created", ticketNos.size());
        r.put("resolved", resolved);
        r.put("pending", ticketNos.size() - resolved);
        r.put("ticketNos", ticketNos);
        return r;
    }

    /** 创建单条工单（含角色分配、消息与时间线），返回业务单号 */
    private String createOne(int index, List<Long> customers, List<Long> agents,
                             List<Map<String, Object>> categories, boolean autoResolve) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Template tpl = TEMPLATES[rnd.nextInt(TEMPLATES.length)];
        Long customerId = customers.get(rnd.nextInt(customers.size()));
        Map<String, Object> category = pickCategory(categories, tpl.category());
        Long categoryId = category == null ? null : ((Number) category.get("id")).longValue();
        int slaHours = category == null || category.get("sla_hours") == null
                ? 24 : ((Number) category.get("sla_hours")).intValue();

        LocalDateTime now = LocalDateTime.now();
        // 自动解决的工单往前推，保证响应/解决时间都落在过去且先后有序
        int respGap = 5 + rnd.nextInt(21);
        int acceptGap = 1 + rnd.nextInt(10);
        int processGap = 30 + rnd.nextInt(121);
        int confirmGap = 5 + rnd.nextInt(36);
        int backMinutes = autoResolve ? respGap + acceptGap + processGap + confirmGap + 10 + rnd.nextInt(180)
                : 1 + rnd.nextInt(30);
        LocalDateTime createdAt = now.minusMinutes(backMinutes);
        LocalDateTime firstResponseAt = createdAt.plusMinutes(respGap);
        LocalDateTime acceptedAt = firstResponseAt.plusMinutes(acceptGap);
        LocalDateTime resolvedAt = acceptedAt.plusMinutes(processGap);
        LocalDateTime closedAt = resolvedAt.plusMinutes(confirmGap);
        String solution = autoResolve ? solutionFor(tpl.category()) : null;
        // 评价文案只取一次，保证工单字段与客户确认消息一致
        String fb = autoResolve ? feedback() : null;
        Long agentId = autoResolve ? agents.get(rnd.nextInt(agents.size())) : null;
        String status = autoResolve ? "RESOLVED" : "PENDING";

        String ticketNo = newTicketNo(index);
        Long ticketId = insertTicket(ticketNo, tpl, customerId, agentId, categoryId, status, solution,
                slaHours, autoResolve, firstResponseAt, resolvedAt, fb, createdAt);

        // 时间线：创建工单
        insertHistory(ticketId, customerId, "CREATE", null, "PENDING", "客户创建工单", createdAt);
        if (autoResolve) {
            Long supervisorId = firstSupervisorId();
            insertHistory(ticketId, supervisorId, "ASSIGN", null, String.valueOf(agentId), "自动分配给客服", createdAt);
            insertHistory(ticketId, agentId, "ACCEPT", "ASSIGNED", "PROCESSING", "客服接单", acceptedAt);
            // 客服公开回复（解决方案），同时作为客户看到的沟通记录
            insertMessage(ticketId, agentId, "PUBLIC", solution, resolvedAt);
            insertHistory(ticketId, agentId, "RESOLVE", "PROCESSING", "WAITING", "提交解决方案", resolvedAt);
            insertMessage(ticketId, customerId, "PUBLIC", fb, closedAt);
            insertHistory(ticketId, customerId, "CONFIRM", "WAITING", "RESOLVED", "客户确认已解决", closedAt);
        }
        return ticketNo;
    }

    /** 插入工单主表并返回自增 ID */
    private Long insertTicket(String ticketNo, Template tpl, Long customerId, Long agentId, Long categoryId,
                              String status, String solution, int slaHours, boolean autoResolve,
                              LocalDateTime firstResponseAt, LocalDateTime resolvedAt, String feedback,
                              LocalDateTime createdAt) {
        String sql = "INSERT INTO ticket(ticket_no, title, description, customer_id, assignee_id, category_id, "
                + "priority, status, source, solution, satisfaction, feedback, sla_response_at, sla_resolve_at, "
                + "first_response_at, resolved_at, reopen_count, created_at, updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, ticketNo);
            ps.setString(2, tpl.title());
            ps.setString(3, tpl.desc());
            ps.setLong(4, customerId);
            ps.setObject(5, agentId);
            ps.setObject(6, categoryId);
            ps.setString(7, tpl.priority());
            ps.setString(8, status);
            ps.setString(9, "WEB");
            ps.setString(10, solution);
            ps.setObject(11, autoResolve ? 4 + ThreadLocalRandom.current().nextInt(2) : null);
            ps.setString(12, feedback);
            ps.setObject(13, createdAt.plusHours(2));
            ps.setObject(14, createdAt.plusHours(Math.max(slaHours, 1)));
            ps.setObject(15, autoResolve ? firstResponseAt : null);
            ps.setObject(16, autoResolve ? resolvedAt : null);
            ps.setObject(17, createdAt);
            ps.setObject(18, autoResolve ? resolvedAt : createdAt);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException("工单写入失败");
        }
        return key.longValue();
    }

    /** 写入工单时间线 */
    private void insertHistory(Long ticketId, Long operatorId, String action, String oldValue,
                               String newValue, String remark, LocalDateTime at) {
        jdbc.update("INSERT INTO ticket_history(ticket_id, operator_id, action, old_value, new_value, remark, created_at) "
                + "VALUES(?,?,?,?,?,?,?)", ticketId, operatorId, action, oldValue, newValue, remark, at);
    }

    /** 写入工单消息 */
    private void insertMessage(Long ticketId, Long senderId, String type, String content, LocalDateTime at) {
        if (content == null) {
            return;
        }
        jdbc.update("INSERT INTO ticket_message(ticket_id, sender_id, message_type, content, created_at) "
                + "VALUES(?,?,?,?,?)", ticketId, senderId, type, content, at);
    }

    /** 生成业务单号：ISD + 秒级时间戳（按批次偏移）+ 4 位随机，与手工创建格式一致 */
    private String newTicketNo(int index) {
        long second = System.currentTimeMillis() / 1000 + index;
        return "ISD" + second + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 按模板期望的分类名匹配分类，找不到则随机取一个 */
    private Map<String, Object> pickCategory(List<Map<String, Object>> categories, String wanted) {
        if (categories.isEmpty()) {
            return null;
        }
        for (Map<String, Object> c : categories) {
            if (wanted.equals(String.valueOf(c.get("name")))) {
                return c;
            }
        }
        return categories.get(ThreadLocalRandom.current().nextInt(categories.size()));
    }

    /** 查询第一个主管 ID，用于时间线里的「分配」操作人 */
    private Long firstSupervisorId() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE role = 'SUPERVISOR' AND status = 1 ORDER BY id LIMIT 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 查询 ID 列表（通用小工具） */
    private List<Long> ids(String sql) {
        return jdbc.queryForList(sql, Long.class);
    }

    /** 按分类返回客服回复文案 */
    private String solutionFor(String categoryName) {
        return SOLUTIONS.getOrDefault(categoryName, SOLUTIONS.get("默认"));
    }

    /** 随机取一条客户评价 */
    private String feedback() {
        String[] list = {"问题已解决，谢谢！", "处理速度很快，感谢支持。", "已经恢复正常，辛苦了。", "客服很耐心，已解决。"};
        return list[ThreadLocalRandom.current().nextInt(list.length)];
    }

    /** 工单模板：标题 / 描述 / 期望分类 / 优先级 */
    private record Template(String title, String desc, String category, String priority) {
    }

    /** 常见问题模板池 */
    private static final Template[] TEMPLATES = {
            new Template("无法登录系统", "从今天上午开始输入正确的账号密码登录，页面一直提示「账号或密码错误」，已确认大小写与输入法无误，请协助排查。", "账户问题", "HIGH"),
            new Template("忘记密码无法重置", "手机号已经更换，收不到重置验证码，导致无法自行重置密码，需要人工协助。", "账户问题", "MEDIUM"),
            new Template("收不到短信验证码", "多次点击发送验证码都提示成功，但手机一直没有收到短信，换另一台手机也一样。", "账户问题", "URGENT"),
            new Template("移动端 App 打开就闪退", "App 升级到最新版本后，一打开就闪退，重装后仍然如此，安卓和 iOS 设备都有出现。", "技术故障", "HIGH"),
            new Template("上传附件一直失败", "上传 PDF 附件时进度条走到 90% 左右就提示上传失败，文件大小约 8 MB。", "技术故障", "MEDIUM"),
            new Template("列表页面加载很慢偶尔超时", "工单列表翻页时经常要等十几秒，偶尔直接提示请求超时，其他页面正常。", "技术故障", "MEDIUM"),
            new Template("账单金额与实际订单不符", "本月账单金额比订单明细合计多出一百多元，麻烦核对一下具体差异来源。", "账单与支付", "HIGH"),
            new Template("申请退款后一直未到账", "上周提交的退款申请显示已通过，但银行卡至今没有收到退款，麻烦帮忙查一下进度。", "账单与支付", "MEDIUM"),
            new Template("如何批量导出报表", "需要按月批量导出工单统计报表，目前只能一条条导，想了解有没有批量导出的方式。", "产品咨询", "LOW"),
            new Template("套餐升级后功能没有生效", "昨天升级到专业版，但高级报表和成员上限还是旧的限制，需要帮忙确认是否生效。", "产品咨询", "MEDIUM"),
            new Template("建议增加工单批量导出功能", "希望工单列表支持勾选多条后一次性导出，方便每周做汇总。", "意见建议", "LOW"),
            new Template("希望支持扫码登录", "团队成员经常忘记密码，希望能支持企业微信或微信扫码登录，减少账号问题。", "意见建议", "LOW")
    };

    /** 分类 → 客服解决方案文案 */
    private static final Map<String, String> SOLUTIONS = Map.of(
            "账户问题", "已协助重置账号密码，临时密码通过短信发送到绑定手机，登录后请立即修改。如仍未收到，请回复本工单。",
            "技术故障", "已定位为客户端缓存异常，请清理缓存并升级到最新版本后重试。若仍有问题，请提供截图与设备型号，我们继续跟进。",
            "账单与支付", "已核对账单明细，差额部分来自上期未结余量。退款将在 1-3 个工作日原路返回到账户，请留意到账通知。",
            "产品咨询", "相关入口在「控制台 - 数据 - 报表」，支持按时间范围批量导出。已附上操作指引，按步骤操作即可完成。",
            "意见建议", "建议已记录并同步给产品团队评估，后续版本会优先考虑，感谢你的反馈。",
            "默认", "问题已处理完成，请留意系统提示。如仍有异常，可直接回复本工单继续跟进。"
    );
}
