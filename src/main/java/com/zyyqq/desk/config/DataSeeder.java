package com.zyyqq.desk.config;

import com.zyyqq.desk.security.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 演示数据初始化：空库时填充用户/分类/工单/通知，并幂等补齐知识库、收藏与近期工单 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    /** 近期补建工单使用的标题池 */
    private static final String[] RECENT_TITLES = {
            "无法登录系统，提示账号或密码错误", "账户余额显示异常", "需要导出本季度数据报表",
            "页面加载缓慢，影响使用", "申请退款，订单未生效", "如何升级到企业版套餐",
            "API 调用返回 401 错误", "想了解产品新功能", "发票抬头需要修改",
            "二次验证绑定失败", "支付成功后订单状态未更新", "希望增加批量导入功能",
            "手机收不到验证码", "系统偶发闪退", "数据统计与实际情况不符",
            "账号疑似被盗", "建议增加夜间模式", "合同电子章无法生成",
            "咨询续费优惠政策", "告警通知未收到", "文件上传失败",
            "权限分配不合理", "报表导出中文乱码", "接口响应超时",
            "希望支持微信登录", "客服响应速度慢", "登录后频繁掉线",
            "退款进度查询", "账单金额核对", "如何解绑旧设备"
    };

    /** 近期补建工单使用的客服回复文案 */
    private static final String[] RECENT_REPLIES = {
            "已收到您的反馈，正在为您核实，请稍等。",
            "方便提供一下截图或操作时间吗？这样能更快定位。",
            "问题已复现，正在处理中，有进展第一时间同步您。",
            "已为您提交处理，预计今天内给出结果。",
            "麻烦您清理浏览器缓存后重试，还不行请告诉我。"
    };

    /** 补齐演示主管时使用的姓名池（既有账号为 supervisor/王主管，新补的从 supervisor1 起算） */
    private static final String[] SUPERVISOR_NAMES = {
            "李主管", "赵主管", "孙主管", "周主管", "吴主管", "郑主管"
    };

    /** 补齐演示客服时使用的姓名池 */
    private static final String[] AGENT_NAMES = {
            "张伟", "李娜", "刘强", "陈静", "杨洋", "赵敏", "孙磊", "周婷", "吴晨", "徐蕾",
            "马超", "朱琳", "郑凯", "何静", "高远", "罗薇", "梁爽", "宋涛", "唐悦", "韩冬"
    };

    /** 补齐演示客户时使用的姓名池（前 10 位对应已有 customer1~10，新增从第 11 位起） */
    private static final String[] CUSTOMER_NAMES = {
            "陈先生", "林女士", "黄同学", "徐女士", "马先生", "朱小姐", "郑先生", "何女士", "高先生", "罗女士",
            "苏先生", "蒋女士", "蔡同学", "贾女士", "丁先生", "卢女士", "郝先生", "孔女士", "崔先生", "龚女士"
    };

    /** 内置分类的说明文案（分类列表里显示在名称下方，也参与搜索） */
    private static final Map<String, String> CATEGORY_DESC = new LinkedHashMap<>();

    static {
        CATEGORY_DESC.put("账户问题", "账号登录、密码找回、权限问题");
        CATEGORY_DESC.put("产品咨询", "产品功能、版本、使用方法");
        CATEGORY_DESC.put("技术故障", "系统报错、功能异常、性能问题");
        CATEGORY_DESC.put("账单与支付", "费用、发票、支付问题");
        CATEGORY_DESC.put("意见建议", "功能建议、使用反馈");
        CATEGORY_DESC.put("其他", "其他未分类问题");
    }

    private final JdbcTemplate jdbc;

    public DataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 启动时检查空库则初始化，并补齐知识库与近期工单演示数据 */
    @Override
    public void run(String... args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        if (count == null || count == 0) {
            seed();
            log.info("演示数据初始化完成");
        } else {
            log.info("已存在用户数据，跳过演示数据初始化");
        }
        ensureUsers();
        ensureCategoryDesc();
        ensureKnowledge();
        ensureFavorites();
        ensureRecentTickets();
        // 顺序有依赖：先补新解决的工单，再补这些工单的评价，最后才按业务表反推操作日志
        ensureResolvedTickets();
        ensureEvaluations();
        alignCloseHistory();
        rebuildOperationLogs();
    }

    /**
     * 已关闭但缺「关闭」历史记录的工单补一条：closed_at 与状态本身就是既成事实，
     * 只是早期种子数据没写留痕，补齐后日志才能和工单状态完全对上。
     */
    private void alignCloseHistory() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id, t.assignee_id, t.closed_at FROM ticket t " +
                        "WHERE t.status = 'CLOSED' AND t.closed_at IS NOT NULL " +
                        "AND NOT EXISTS (SELECT 1 FROM ticket_history h " +
                        "                WHERE h.ticket_id = t.id AND h.action = 'CLOSE')");
        int done = 0;
        for (Map<String, Object> row : rows) {
            insertHistory(((Number) row.get("id")).longValue(), row.get("assignee_id"), "CLOSE",
                    "RESOLVED", "CLOSED", "关闭工单", toDateTime(row.get("closed_at")));
            done++;
        }
        if (done > 0) {
            log.info("已为 {} 张已关闭工单补齐关闭记录", done);
        }
    }

    /** 初始化用户、分类、知识、工单与通知 */
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

        // ===== 工单与通知（知识库统一由 ensureKnowledge 补齐） =====
        seedTickets(adminId, supId, cats);

        seedNotification(adminId, supId);
    }

    /** 插入各类演示用户并返回管理员/主管 id */
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

    /** 插入单个用户并返回 id */
    private long insertUser(String username, String displayName, String role, String phone, String dept, String color) {
        return insertAndGetId(
                "INSERT INTO sys_user(username,password_hash,display_name,role,phone,department,avatar_color,status) " +
                        "VALUES(?,?,?,?,?,?,?,1)",
                username, PasswordUtil.hash("123456"), displayName, role, phone, dept, color);
    }

    /** 补齐演示用户规模：管理员固定 1 个，主管 4、客服 18、客户 25，总数 48（幂等） */
    private void ensureUsers() {
        ensureRoleCount("SUPERVISOR", "supervisor", SUPERVISOR_NAMES, 4, null, "#8b5cf6");
        ensureRoleCount("AGENT", "agent", AGENT_NAMES, 18, "客服部", "#3b82f6");
        ensureRoleCount("CUSTOMER", "customer", CUSTOMER_NAMES, 25, null, "#06b6d4");
    }

    /** 把某一角色补到目标人数：按「前缀+序号」生成账号，已存在则跳过，重复启动不会重复插入 */
    private void ensureRoleCount(String role, String prefix, String[] names, int target, String dept, String color) {
        Long current = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE role = ?", Long.class, role);
        int need = target - (current == null ? 0 : current.intValue());
        if (need <= 0) {
            return;
        }
        int inserted = 0;
        for (int i = 1; i <= names.length && inserted < need; i++) {
            String username = prefix + i;
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Long.class, username);
            if (exists != null && exists > 0) {
                continue;
            }
            insertUser(username, names[i - 1], role, nextPhone(role, i), dept, color);
            inserted++;
        }
        log.info("补齐 {} 用户 {} 个（目标 {} 人）", role, inserted, target);
    }

    /** 给内置分类补齐说明文案：只填空值，不覆盖管理员自己改过的说明 */
    private void ensureCategoryDesc() {
        int filled = 0;
        for (Map.Entry<String, String> entry : CATEGORY_DESC.entrySet()) {
            filled += jdbc.update(
                    "UPDATE ticket_category SET description = ? WHERE name = ? AND (description IS NULL OR description = '')",
                    entry.getValue(), entry.getKey());
        }
        if (filled > 0) {
            log.info("已为 {} 个内置分类补充说明文案", filled);
        }
    }

    /** 按角色生成演示手机号，避免不同角色之间重复 */
    private String nextPhone(String role, int seq) {
        String head = switch (role) {
            case "SUPERVISOR" -> "1380002";
            case "AGENT" -> "1380003";
            default -> "1390002";
        };
        return head + String.format("%04d", seq);
    }

    /** 执行插入并返回自增主键（参数绑定交给 Spring，兼容 null 值） */
    private long insertAndGetId(String sql, Object... args) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            new org.springframework.jdbc.core.ArgumentPreparedStatementSetter(args).setValues(ps);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    /** 知识分类按名称取 id，不存在则创建（幂等） */
    private long ensureKnowledgeCategory(String name, int sort) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM knowledge_category WHERE name = ?", Long.class, name);
        return ids.isEmpty()
                ? insertAndGetId("INSERT INTO knowledge_category(name,sort) VALUES(?,?)", name, sort)
                : ids.get(0);
    }

    /**
     * 补齐知识库文章到 75 篇：新增缺失文章、把旧版短文替换为详细正文，
     * 并给每篇分配互不相同的发布时间与更新时间
     */
    private void ensureKnowledge() {
        try {
            Long adminId = jdbc.queryForObject(
                    "SELECT id FROM sys_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1", Long.class);
            if (adminId == null) {
                return;
            }
            String[] names = {"使用指南", "常见问题", "账号安全", "账单与退款", "技术故障", "产品功能"};
            Map<String, Long> catIds = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) {
                catIds.put(names[i], ensureKnowledgeCategory(names[i], i + 1));
            }

            Random rnd = new Random(20261027L);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();
            List<KnowledgeSeedData.SeedArticle> all = KnowledgeSeedData.all();
            int inserted = 0;
            int expanded = 0;

            for (int i = 0; i < all.size(); i++) {
                KnowledgeSeedData.SeedArticle s = all.get(i);
                // 用与总数互质的步长打乱槽位，让文章列表的时间顺序不跟随插入顺序
                long slot = (i * 37L) % all.size();
                // 每篇文章错开 23 / 17 小时并叠加随机分钟，保证 75 篇文章的发布与更新时间各不相同
                LocalDateTime published = now.minusHours(6 + slot * 23 + rnd.nextInt(11)).minusMinutes(rnd.nextInt(60));
                LocalDateTime updated = now.minusHours(2 + slot * 17 + rnd.nextInt(9)).minusMinutes(rnd.nextInt(60));
                // 极端情况下更新时间可能落到发布时间之前，回退为发布后 12 小时
                if (!updated.isAfter(published)) {
                    updated = published.plusHours(12);
                }
                if (updated.isAfter(now)) {
                    updated = now.minusMinutes(30L + i);
                }
                // 浏览量控制在 80~320：按 5%~11% 折算后的收藏量刚好落在真实用户数之内
                int views = 80 + rnd.nextInt(241);

                List<Long> ids = jdbc.queryForList(
                        "SELECT id FROM knowledge_article WHERE title = ?", Long.class, s.title());
                if (ids.isEmpty()) {
                    jdbc.update("INSERT INTO knowledge_article(title,summary,body,category_id,keywords,status,author_id," +
                                    "view_count,published_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                            s.title(), s.summary(), s.body(), catIds.get(s.category()), s.keywords(),
                            "PUBLISHED", adminId, views,
                            published.format(fmt), published.format(fmt), updated.format(fmt));
                    inserted++;
                    continue;
                }
                Long id = ids.get(0);
                Integer bodyLenObj = jdbc.queryForObject(
                        "SELECT COALESCE(CHAR_LENGTH(body), 0) FROM knowledge_article WHERE id = ?", Integer.class, id);
                Integer viewsObj = jdbc.queryForObject(
                        "SELECT view_count FROM knowledge_article WHERE id = ?", Integer.class, id);
                int bodyLen = bodyLenObj == null ? 0 : bodyLenObj;
                int curViews = viewsObj == null ? 0 : viewsObj;
                // 旧版正文不足 300 字的短文统一替换为详细版，保证 90% 以上文章都有完整说明
                boolean expand = bodyLen < 300;
                // 浏览量超过 600 时按 5% 折算的收藏量会超过真实用户数，比例失真，这里重新分配
                boolean resetViews = curViews < 80 || curViews > 600;
                if (!expand && !resetViews) {
                    continue;
                }
                if (expand) {
                    jdbc.update("UPDATE knowledge_article SET summary = ?, body = ?, keywords = ?, category_id = ? WHERE id = ?",
                            s.summary(), s.body(), s.keywords(), catIds.get(s.category()), id);
                    expanded++;
                }
                if (resetViews) {
                    jdbc.update("UPDATE knowledge_article SET view_count = ? WHERE id = ?", views, id);
                }
                // 上面的 UPDATE 会触发 ON UPDATE CURRENT_TIMESTAMP，这里再显式覆盖为差异化的更新时间
                jdbc.update("UPDATE knowledge_article SET updated_at = ? WHERE id = ?", updated.format(fmt), id);
            }
            log.info("知识库数据检查完成：新增 {} 篇，扩充详细正文 {} 篇，共 {} 篇", inserted, expanded, all.size());
        } catch (Exception e) {
            log.warn("知识库数据补充失败（不影响启动）：{}", e.getMessage());
        }
    }

    /** 补充收藏记录：收藏量按浏览量的 5%~11% 随机生成，向上取整确保不低于 5%，收藏人随机抽取 */
    private void ensureFavorites() {
        try {
            List<Long> users = jdbc.queryForList("SELECT id FROM sys_user ORDER BY id", Long.class);
            List<Map<String, Object>> articles = jdbc.queryForList(
                    "SELECT id, view_count FROM knowledge_article ORDER BY id");
            if (users.isEmpty() || articles.isEmpty()) {
                return;
            }
            Random rnd = new Random(20261028L);
            int added = 0;
            for (Map<String, Object> a : articles) {
                long articleId = ((Number) a.get("id")).longValue();
                Object viewsObj = a.get("view_count") != null ? a.get("view_count") : a.get("viewCount");
                int views = viewsObj == null ? 0 : ((Number) viewsObj).intValue();
                // 收藏率按文章 ID 做种子，同一篇文章每次计算出的目标值固定，保证重复启动幂等
                double rate = 0.05 + new Random(articleId * 1000003L + 7L).nextDouble() * 0.06;
                int target = Math.min(Math.max((int) Math.ceil(views * rate), 3), users.size());
                Set<Long> exists = new HashSet<>(jdbc.queryForList(
                        "SELECT user_id FROM knowledge_favorite WHERE article_id = ?", Long.class, articleId));
                int need = target - exists.size();
                // 少量差额不补齐，避免用户手动取消收藏后重启又被写回
                if (need < 2) {
                    continue;
                }
                List<Long> pool = new ArrayList<>(users);
                Collections.shuffle(pool, rnd);
                for (Long uid : pool) {
                    if (need <= 0) {
                        break;
                    }
                    if (exists.contains(uid)) {
                        continue;
                    }
                    jdbc.update("INSERT IGNORE INTO knowledge_favorite(article_id, user_id) VALUES(?,?)", articleId, uid);
                    need--;
                    added++;
                }
            }
            if (added > 0) {
                log.info("知识库收藏数据补充完成：新增 {} 条", added);
            }
        } catch (Exception e) {
            log.warn("知识库收藏数据补充失败（不影响启动）：{}", e.getMessage());
        }
    }

    /** 补齐最近 7 天的工单量：每天不足目标条数时补建，已达目标的日期不再重复插入 */
    private void ensureRecentTickets() {
        try {
            List<Long> agents = jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE role = 'AGENT' AND status = 1 ORDER BY id", Long.class);
            List<Long> customers = jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE role = 'CUSTOMER' AND status = 1 ORDER BY id", Long.class);
            List<Map<String, Object>> cats = jdbc.queryForList(
                    "SELECT id, sla_hours FROM ticket_category WHERE status = 1 ORDER BY id");
            if (agents.isEmpty() || customers.isEmpty() || cats.isEmpty()) {
                return;
            }
            // 索引 0 为今天，其余依次为前 1~6 天；整体较前期抬升，趋势图上能看到近期放量
            int[] targets = {58, 64, 61, 69, 56, 63, 60};
            Random rnd = new Random(20261029L);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();
            int created = 0;

            for (int d = 0; d < targets.length; d++) {
                LocalDate day = LocalDate.now().minusDays(d);
                LocalDateTime dayStart = day.atStartOfDay().plusHours(8);
                // 今天只统计到当前时刻，避免把工单时间写到未来
                LocalDateTime dayEnd = d == 0 ? now : day.atStartOfDay().plusHours(21);
                if (!dayEnd.isAfter(dayStart)) {
                    continue;
                }
                Long exists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ticket WHERE created_at >= ? AND created_at < ?", Long.class,
                        day.atStartOfDay(), day.plusDays(1).atStartOfDay());
                int target = d == 0
                        ? (int) Math.round(targets[0] * Math.min(dayEnd.getHour() + dayEnd.getMinute() / 60.0, 24) / 24.0)
                        : targets[d];
                int need = target - (exists == null ? 0 : exists.intValue());
                long spanMinutes = Math.max(Duration.between(dayStart, dayEnd).toMinutes(), 1);
                for (int i = 0; i < need; i++) {
                    created += insertRecentTicket(dayStart, dayEnd, spanMinutes, d == 0,
                            customers, agents, cats, rnd, fmt);
                }
            }
            if (created > 0) {
                log.info("近期工单数据补充完成：新增 {} 条", created);
            }
        } catch (Exception e) {
            log.warn("近期工单数据补充失败（不影响启动）：{}", e.getMessage());
        }
    }

    /** 补建单条近期工单（含时间线与消息），返回 1 表示成功、0 表示跳过 */
    private int insertRecentTicket(LocalDateTime dayStart, LocalDateTime dayEnd, long spanMinutes,
                                   boolean today, List<Long> customers, List<Long> agents,
                                   List<Map<String, Object>> cats, Random rnd, DateTimeFormatter fmt) {
        String title = RECENT_TITLES[rnd.nextInt(RECENT_TITLES.length)];
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = dayStart.plusMinutes((long) rnd.nextInt((int) spanMinutes));
        if (createdAt.isAfter(now.minusMinutes(10))) {
            createdAt = now.minusMinutes(10);
        }
        Map<String, Object> cat = cats.get(rnd.nextInt(cats.size()));
        long categoryId = ((Number) cat.get("id")).longValue();
        int slaHours = cat.get("sla_hours") == null ? 24 : ((Number) cat.get("sla_hours")).intValue();

        // 当天创建的工单只可能处于进行中状态，避免出现解决时间早于创建时间的脏数据
        String status = today
                ? weighted(new String[]{"PENDING", "ASSIGNED", "PROCESSING", "WAITING"},
                new double[]{0.22, 0.18, 0.52, 0.08}, rnd)
                : weighted(new String[]{"PENDING", "ASSIGNED", "PROCESSING", "WAITING", "RESOLVED", "CLOSED"},
                new double[]{0.14, 0.13, 0.24, 0.04, 0.25, 0.20}, rnd);
        String priority = weighted(new String[]{"URGENT", "HIGH", "MEDIUM", "LOW"},
                new double[]{0.08, 0.24, 0.52, 0.16}, rnd);
        String[] sources = {"WEB", "PHONE", "EMAIL", "WECHAT"};
        String source = sources[rnd.nextInt(sources.length)];
        Long customerId = customers.get(rnd.nextInt(customers.size()));
        boolean pending = "PENDING".equals(status);
        Long assigneeId = pending ? null : agents.get(rnd.nextInt(agents.size()));

        LocalDateTime slaResponseAt = createdAt.plusHours(2);
        LocalDateTime slaResolveAt = createdAt.plusHours(slaHours);
        LocalDateTime firstResponseAt = pending ? null : createdAt.plusMinutes(5 + rnd.nextInt(120));
        if (firstResponseAt != null && firstResponseAt.isAfter(now)) {
            firstResponseAt = now.minusMinutes(1);
        }
        LocalDateTime resolvedAt = null;
        LocalDateTime closedAt = null;
        String solution = null;
        Integer satisfaction = null;
        String feedback = null;

        if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
            // 解决时间取首次响应后 30~540 分钟，且必须早于当前时刻，否则降级为处理中
            LocalDateTime base = firstResponseAt == null ? createdAt : firstResponseAt;
            LocalDateTime candidate = base.plusMinutes(30 + rnd.nextInt(511));
            if (candidate.isAfter(now)) {
                status = "PROCESSING";
            } else {
                resolvedAt = candidate;
                solution = "已协助客户处理：" + title + "。\n如仍有疑问欢迎随时联系。";
                satisfaction = 3 + rnd.nextInt(3);
                feedback = rnd.nextBoolean() ? "处理得很及时，非常满意。" : "问题已解决，谢谢。";
                if ("CLOSED".equals(status)) {
                    LocalDateTime closeCandidate = resolvedAt.plusMinutes(30 + rnd.nextInt(600));
                    if (closeCandidate.isAfter(now)) {
                        status = "RESOLVED";
                    } else {
                        closedAt = closeCandidate;
                    }
                }
            }
        }
        // 最后一次变更时间用于 updated_at
        LocalDateTime lastTouch = closedAt != null ? closedAt : (resolvedAt != null ? resolvedAt : createdAt);

        String ticketNo = "ISD" + createdAt.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + String.format("%03d", rnd.nextInt(1000));
        try {
            Long ticketId = insertAndGetId(
                    "INSERT INTO ticket(ticket_no,title,description,customer_id,assignee_id,category_id," +
                            "priority,status,source,solution,satisfaction,feedback,sla_response_at,sla_resolve_at," +
                            "first_response_at,resolved_at,closed_at,reopen_count,created_at,updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    ticketNo, title, "客户反馈：" + title + "。\n期望尽快处理，谢谢。",
                    customerId, assigneeId, categoryId, priority, status, source, solution, satisfaction, feedback,
                    slaResponseAt.format(fmt), slaResolveAt.format(fmt),
                    fmt(firstResponseAt, fmt), fmt(resolvedAt, fmt), fmt(closedAt, fmt),
                    0, createdAt.format(fmt), lastTouch.format(fmt));

            jdbc.update("INSERT INTO ticket_history(ticket_id,operator_id,action,old_value,new_value,remark,created_at) " +
                            "VALUES(?,?,?,?,?,?,?)",
                    ticketId, customerId, "CREATE", null, "PENDING", "客户创建工单", createdAt.format(fmt));
            if (assigneeId != null) {
                jdbc.update("INSERT INTO ticket_history(ticket_id,operator_id,action,old_value,new_value,remark,created_at) " +
                                "VALUES(?,?,?,?,?,?,?)",
                        ticketId, assigneeId, "ASSIGN", "PENDING", "ASSIGNED", "工单已分派",
                        createdAt.plusMinutes(5).format(fmt));
            }
            if (resolvedAt != null) {
                jdbc.update("INSERT INTO ticket_history(ticket_id,operator_id,action,old_value,new_value,remark,created_at) " +
                                "VALUES(?,?,?,?,?,?,?)",
                        ticketId, assigneeId, "RESOLVE", "PROCESSING", "RESOLVED", "提交解决方案", resolvedAt.format(fmt));
            }
            jdbc.update("INSERT INTO ticket_message(ticket_id,sender_id,message_type,content,created_at) VALUES(?,?,?,?,?)",
                    ticketId, customerId, "PUBLIC", "客户反馈：" + title + "，麻烦帮忙看一下。", createdAt.format(fmt));
            if (assigneeId != null && firstResponseAt != null) {
                jdbc.update("INSERT INTO ticket_message(ticket_id,sender_id,message_type,content,created_at) VALUES(?,?,?,?,?)",
                        ticketId, assigneeId, "PUBLIC", RECENT_REPLIES[rnd.nextInt(RECENT_REPLIES.length)],
                        firstResponseAt.format(fmt));
            }
            return 1;
        } catch (Exception e) {
            // 单号冲突等偶发失败直接跳过，不影响其余数据
            return 0;
        }
    }

    /** 批量生成演示工单数据 */
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

    /** 为工单补充消息与历史记录 */
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

    /** 初始化演示通知 */
    private void seedNotification(long adminId, long supId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = LocalDateTime.now().format(fmt);
        jdbc.update("INSERT INTO notification(recipient_id,type,title,content,is_read,created_at) VALUES(?,?,?,?,?,?)",
                adminId, "SYSTEM", "系统上线通知", "智能客服与工单管理平台已成功部署上线。", 0, now);
        jdbc.update("INSERT INTO notification(recipient_id,type,title,content,is_read,created_at) VALUES(?,?,?,?,?,?)",
                supId, "TICKET", "有待分配工单", "当前存在待分配工单，请及时处理。", 0, now);
    }

    /** 时间格式化（null 安全） */
    private String fmt(LocalDateTime t, DateTimeFormatter fmt) {
        return t == null ? null : t.format(fmt);
    }

    /** 按权重随机选取一个元素 */
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

    /** 工单历史动作 → 操作日志动作的映射 */
    private static final Map<String, String> HISTORY_ACTION_MAP = new LinkedHashMap<>();

    static {
        HISTORY_ACTION_MAP.put("CREATE", "CREATE_TICKET");
        HISTORY_ACTION_MAP.put("APPLY", "APPLY_TICKET");
        HISTORY_ACTION_MAP.put("REVIEW", "REVIEW_TICKET");
        HISTORY_ACTION_MAP.put("ASSIGN", "ASSIGN_TICKET");
        HISTORY_ACTION_MAP.put("ACCEPT", "ACCEPT_TICKET");
        HISTORY_ACTION_MAP.put("RESOLVE", "RESOLVE_TICKET");
        HISTORY_ACTION_MAP.put("CONFIRM", "CONFIRM_TICKET");
        HISTORY_ACTION_MAP.put("CLOSE", "CLOSE_TICKET");
        HISTORY_ACTION_MAP.put("REOPEN", "REOPEN_TICKET");
        HISTORY_ACTION_MAP.put("RATE", "RATE_TICKET");
        HISTORY_ACTION_MAP.put("URGE", "URGE_TICKET");
    }

    /** 各星级对应的评语池，保证评语与星级语义匹配 */
    private static final Map<Integer, String[]> RATE_COMMENTS = new LinkedHashMap<>();

    static {
        RATE_COMMENTS.put(5, new String[]{
                "响应非常快，问题一次就解决了，感谢！",
                "客服很专业，耐心解释到我能自己操作，五星好评。",
                "处理效率很高，晚上提交的第二天一早就好了。",
                "态度特别好，全程跟进，问题解决得很彻底。",
                "给客服点赞，方案写得很清楚，照着做就好了。"
        });
        RATE_COMMENTS.put(4, new String[]{
                "整体满意，就是首次回复稍微慢了一点。",
                "问题解决了，希望以后能有更详细的进度同步。",
                "服务态度不错，处理过程还算顺利。",
                "方案有效，如果能提前告知预计时间就更好了。"
        });
        RATE_COMMENTS.put(3, new String[]{
                "问题是解决了，但来回沟通了好几次。",
                "等待时间有点长，结果还行。",
                "处理结果可以接受，过程不太顺畅。"
        });
        RATE_COMMENTS.put(2, new String[]{
                "响应速度偏慢，中途换了两个人对接。",
                "解决方案不是很对路，最后自己摸索着弄好了。"
        });
        RATE_COMMENTS.put(1, new String[]{
                "等了两天才有人回复，体验比较差。",
                "问题反复出现，没有彻底解决。"
        });
    }

    /** 补充「新解决工单」时使用的解决方案文案池 */
    private static final String[] SOLUTIONS = {
            "已定位为客户侧配置问题，指导客户重新配置后恢复正常。",
            "已清理异常缓存并刷新权限，功能恢复正常。",
            "已为客户补发数据并核对账目，差异已抹平。",
            "已升级到最新版本，问题不再复现。",
            "已协调后端修复接口异常，现可正常调用。"
    };

    /** 近 7 天希望保有的「新解决工单」数量，已有足够则不再补充 */
    private static final int RECENT_RESOLVED_TARGET = 18;

    /** 已解决工单的评价覆盖率目标（客户并非每单都会评价） */
    private static final double RATE_COVERAGE = 0.72;

    /** 应用级标记键：操作日志是否已按真实业务数据重建过 */
    private static final String META_LOG_REBUILT = "operation_log_rebuilt";

    /**
     * 补充近期的「新解决工单」：把进行中的工单推进到已解决，并补上解决方案与流转历史。
     * 以「近 7 天已解决数量」判断是否需要补，重复启动不会把工单无限解决下去。
     */
    private void ensureResolvedTickets() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        long have = count("SELECT COUNT(*) FROM ticket WHERE resolved_at >= ?", since);
        int need = RECENT_RESOLVED_TARGET - (int) have;
        if (need <= 0) {
            return;
        }
        List<Long> agentIds = jdbc.queryForList(
                "SELECT id FROM sys_user WHERE role = 'AGENT' AND status = 1 ORDER BY id", Long.class);
        if (agentIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, assignee_id, created_at FROM ticket " +
                        "WHERE status IN ('PENDING','ASSIGNED','PROCESSING','WAITING') " +
                        "ORDER BY created_at DESC, id DESC LIMIT ?", need);
        Random rnd = new Random(20260202L);
        LocalDateTime now = LocalDateTime.now();
        int done = 0;
        for (Map<String, Object> row : rows) {
            Long ticketId = ((Number) row.get("id")).longValue();
            String status = text(row.get("status"));
            Object assigneeObj = row.get("assignee_id");
            Long assigneeId = assigneeObj == null ? null : ((Number) assigneeObj).longValue();
            LocalDateTime createdAt = toDateTime(row.get("created_at"));
            // 解决时间落在「创建之后、当前时刻之前」，且尽量在最近 7 天内
            LocalDateTime lower = createdAt == null ? now.minusDays(3) : createdAt.plusMinutes(30);
            if (!lower.isBefore(now)) {
                lower = now.minusHours(2);
            }
            long span = Duration.between(lower, now).getSeconds();
            LocalDateTime resolvedAt = span > 0 ? lower.plusSeconds((long) (rnd.nextDouble() * span)) : now.minusHours(1);

            if (assigneeId == null) {
                assigneeId = agentIds.get(rnd.nextInt(agentIds.size()));
                jdbc.update("UPDATE ticket SET assignee_id = ? WHERE id = ?", assigneeId, ticketId);
                insertHistory(ticketId, assigneeId, "ASSIGN", "PENDING", "ASSIGNED", "工单已分派",
                        (createdAt == null ? resolvedAt : createdAt).plusMinutes(5));
            }
            jdbc.update("UPDATE ticket SET status = 'RESOLVED', solution = ?, resolved_at = ?, updated_at = ? WHERE id = ?",
                    SOLUTIONS[rnd.nextInt(SOLUTIONS.length)], resolvedAt, resolvedAt, ticketId);
            insertHistory(ticketId, assigneeId, "RESOLVE", status, "RESOLVED", "提交解决方案", resolvedAt);
            done++;
        }
        if (done > 0) {
            log.info("已补充新解决工单 {} 张", done);
        }
    }

    /** 为已解决工单补充客户评价（星级 + 主观评语），覆盖率控制在 72% 左右，只处理未评价的 */
    private void ensureEvaluations() {
        long total = count("SELECT COUNT(*) FROM ticket WHERE status IN ('RESOLVED','CLOSED')");
        if (total == 0) {
            return;
        }
        long rated = count("SELECT COUNT(*) FROM ticket WHERE satisfaction IS NOT NULL");
        int need = (int) Math.round(total * RATE_COVERAGE) - (int) rated;
        if (need <= 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, customer_id, resolved_at, closed_at FROM ticket " +
                        "WHERE status IN ('RESOLVED','CLOSED') AND satisfaction IS NULL " +
                        "ORDER BY resolved_at DESC, id DESC LIMIT ?", need);
        Random rnd = new Random(20260303L);
        LocalDateTime now = LocalDateTime.now();
        int done = 0;
        for (Map<String, Object> row : rows) {
            Long ticketId = ((Number) row.get("id")).longValue();
            int rating = pickRating(rnd);
            String[] pool = RATE_COMMENTS.get(rating);
            String comment = pool[rnd.nextInt(pool.length)];
            jdbc.update("UPDATE ticket SET satisfaction = ?, feedback = ? WHERE id = ?", rating, comment, ticketId);
            // 评价发生在解决之后 0.5~8 小时内
            LocalDateTime base = toDateTime(row.get("resolved_at"));
            if (base == null) {
                base = toDateTime(row.get("closed_at"));
            }
            LocalDateTime ratedAt = (base == null ? now.minusHours(3) : base).plusMinutes(30 + rnd.nextInt(450));
            if (ratedAt.isAfter(now)) {
                ratedAt = now.minusMinutes(5);
            }
            insertHistory(ticketId, row.get("customer_id"), "RATE", null, String.valueOf(rating), "客户评价", ratedAt);
            done++;
        }
        if (done > 0) {
            log.info("已补充客户评价 {} 条", done);
        }
    }

    /** 按接近真实的分布挑一个星级：5 星 45%、4 星 33%、3 星 14%、2 星 5%、1 星 3% */
    private int pickRating(Random rnd) {
        int p = rnd.nextInt(100);
        if (p < 45) {
            return 5;
        }
        if (p < 78) {
            return 4;
        }
        if (p < 92) {
            return 3;
        }
        if (p < 97) {
            return 2;
        }
        return 1;
    }

    /** 写一条工单流转历史 */
    private void insertHistory(Long ticketId, Object operatorId, String action,
                               String oldValue, String newValue, String remark, LocalDateTime at) {
        jdbc.update("INSERT INTO ticket_history(ticket_id,operator_id,action,old_value,new_value,remark,created_at) " +
                        "VALUES(?,?,?,?,?,?,?)",
                ticketId, operatorId, action, oldValue, newValue, remark,
                at == null ? LocalDateTime.now() : at);
    }

    /**
     * 按真实业务数据重建操作日志：工单流转历史、工单回复、知识文章、用户与分类创建、登录记录。
     * 这样日志里的每一条操作都能在业务表里找到对应实体（日志说关闭了某单，该单状态就是已关闭）。
     * 通过 app_meta 标记保证只重建一次，之后运行期真实产生的日志不会被覆盖。
     */
    private void rebuildOperationLogs() {
        long flag = count("SELECT COUNT(*) FROM app_meta WHERE k = ?", META_LOG_REBUILT);
        if (flag > 0) {
            return;
        }
        int removed = jdbc.update("DELETE FROM operation_log");
        List<Object[]> rows = new ArrayList<>();
        rows.addAll(logsFromTicketHistory());
        rows.addAll(logsFromTicketReplies());
        rows.addAll(logsFromArticles());
        rows.addAll(logsFromAccounts());
        rows.addAll(logsFromLogins());
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO operation_log(operator_id,operator_name,role,action,target_type,target_id," +
                    "detail,status,ip,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)", rows);
        }
        jdbc.update("INSERT INTO app_meta(k, v) VALUES(?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)",
                META_LOG_REBUILT, String.valueOf(rows.size()));
        log.info("操作日志已按真实业务数据重建 {} 条（清理旧演示记录 {} 条）", rows.size(), removed);
    }

    /** 工单流转历史 → 操作日志（时间、操作人、备注全部取自真实历史记录） */
    private List<Object[]> logsFromTicketHistory() {
        List<Object[]> rows = new ArrayList<>();
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT h.action, h.remark, h.created_at, h.operator_id, t.ticket_no, " +
                        "u.display_name, u.role FROM ticket_history h " +
                        "JOIN ticket t ON t.id = h.ticket_id " +
                        "LEFT JOIN sys_user u ON u.id = h.operator_id ORDER BY h.id");
        for (Map<String, Object> m : list) {
            String action = HISTORY_ACTION_MAP.get(text(m.get("action")));
            if (action == null) {
                continue;
            }
            rows.add(logRow(m.get("operator_id"), m.get("display_name"), m.get("role"), action,
                    "TICKET", text(m.get("ticket_no")), text(m.get("remark")), toDateTime(m.get("created_at"))));
        }
        return rows;
    }

    /** 工单回复 → 操作日志（每张工单只取最后一条客服公开回复，避免日志被聊天刷屏） */
    private List<Object[]> logsFromTicketReplies() {
        List<Object[]> rows = new ArrayList<>();
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT m.content, m.created_at, m.sender_id, t.ticket_no, u.display_name, u.role " +
                        "FROM ticket_message m " +
                        "JOIN (SELECT ticket_id, MAX(id) AS mid FROM ticket_message " +
                        "      WHERE message_type = 'PUBLIC' GROUP BY ticket_id) x ON x.mid = m.id " +
                        "JOIN ticket t ON t.id = m.ticket_id " +
                        "JOIN sys_user u ON u.id = m.sender_id " +
                        "WHERE u.role <> 'CUSTOMER' ORDER BY m.id");
        for (Map<String, Object> m : list) {
            String content = text(m.get("content"));
            rows.add(logRow(m.get("sender_id"), m.get("display_name"), m.get("role"), "REPLY_TICKET",
                    "TICKET", text(m.get("ticket_no")),
                    "回复工单：" + (content.length() > 120 ? content.substring(0, 120) + "…" : content),
                    toDateTime(m.get("created_at"))));
        }
        return rows;
    }

    /** 知识文章 → 新建 / 发布 / 更新日志（时间取自文章表真实的创建、发布、更新时间） */
    private List<Object[]> logsFromArticles() {
        List<Object[]> rows = new ArrayList<>();
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT a.id, a.title, a.created_at, a.updated_at, a.published_at, " +
                        "a.author_id, u.display_name, u.role FROM knowledge_article a " +
                        "LEFT JOIN sys_user u ON u.id = a.author_id ORDER BY a.id");
        for (Map<String, Object> m : list) {
            String id = text(m.get("id"));
            String title = text(m.get("title"));
            LocalDateTime createdAt = toDateTime(m.get("created_at"));
            LocalDateTime updatedAt = toDateTime(m.get("updated_at"));
            LocalDateTime publishedAt = toDateTime(m.get("published_at"));
            rows.add(logRow(m.get("author_id"), m.get("display_name"), m.get("role"), "CREATE_ARTICLE",
                    "ARTICLE", id, "新建知识文章《" + title + "》", createdAt));
            if (publishedAt != null) {
                rows.add(logRow(m.get("author_id"), m.get("display_name"), m.get("role"), "PUBLISH_ARTICLE",
                        "ARTICLE", id, "发布知识文章《" + title + "》", publishedAt));
            }
            boolean editedAfterCreate = createdAt != null && updatedAt != null
                    && updatedAt.isAfter(createdAt.plusMinutes(1));
            boolean editedAfterPublish = publishedAt == null || (updatedAt != null
                    && updatedAt.isAfter(publishedAt.plusMinutes(1)));
            if (editedAfterCreate && editedAfterPublish) {
                rows.add(logRow(m.get("author_id"), m.get("display_name"), m.get("role"), "UPDATE_ARTICLE",
                        "ARTICLE", id, "更新知识文章《" + title + "》", updatedAt));
            }
        }
        return rows;
    }

    /** 用户与工单分类的创建记录 → 操作日志（操作人取最早的管理员） */
    private List<Object[]> logsFromAccounts() {
        List<Object[]> rows = new ArrayList<>();
        Map<String, Object> admin = jdbc.queryForList(
                "SELECT id, display_name, role FROM sys_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1")
                .stream().findFirst().orElse(Map.of());
        Object adminId = admin.get("id");
        Object adminName = admin.getOrDefault("display_name", "系统");
        Object adminRole = admin.get("role");

        for (Map<String, Object> m : jdbc.queryForList(
                "SELECT username, display_name, role, created_at FROM sys_user ORDER BY id")) {
            rows.add(logRow(adminId, adminName, adminRole, "CREATE_USER", "USER", text(m.get("username")),
                    "新建用户 " + text(m.get("display_name")) + "（" + text(m.get("role")) + "）",
                    toDateTime(m.get("created_at"))));
        }
        for (Map<String, Object> m : jdbc.queryForList(
                "SELECT name, created_at FROM ticket_category ORDER BY id")) {
            rows.add(logRow(adminId, adminName, adminRole, "CREATE_CATEGORY", "CATEGORY", text(m.get("name")),
                    "新建工单分类「" + text(m.get("name")) + "」", toDateTime(m.get("created_at"))));
        }
        return rows;
    }

    /** 用户的最后登录时间 → 登录日志 */
    private List<Object[]> logsFromLogins() {
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> m : jdbc.queryForList(
                "SELECT id, username, display_name, role, last_login_at FROM sys_user " +
                        "WHERE last_login_at IS NOT NULL ORDER BY id")) {
            rows.add(logRow(m.get("id"), m.get("display_name"), m.get("role"), "LOGIN", "USER",
                    text(m.get("username")), "登录系统成功", toDateTime(m.get("last_login_at"))));
        }
        return rows;
    }

    /** 组装一条操作日志的插入参数（系统尚未上线，IP 统一记本机回环） */
    private Object[] logRow(Object operatorId, Object operatorName, Object role, String action,
                            String targetType, String targetId, String detail, LocalDateTime at) {
        String tid = targetId == null ? null : (targetId.length() > 64 ? targetId.substring(0, 64) : targetId);
        String body = detail == null ? "" : (detail.length() > 1000 ? detail.substring(0, 999) + "…" : detail);
        return new Object[]{operatorId, operatorName, role, action, targetType, tid, body,
                "SUCCESS", "127.0.0.1", at == null ? LocalDateTime.now() : at};
    }

    /** 取字符串值，null 转空串 */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 兼容 JdbcTemplate 返回的 Timestamp / LocalDateTime / Date */
    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime t) {
            return t;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date d) {
            return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
        }
        return null;
    }

    /** 单值计数，null 视为 0 */
    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
