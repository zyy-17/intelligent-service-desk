package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.entity.Ticket;
import com.zyyqq.desk.entity.TicketHistory;
import com.zyyqq.desk.entity.TicketMessage;
import com.zyyqq.desk.repository.KnowledgeArticleRepository;
import com.zyyqq.desk.repository.TicketHistoryRepository;
import com.zyyqq.desk.repository.TicketMessageRepository;
import com.zyyqq.desk.repository.TicketRepository;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工单服务：工单全生命周期（创建/回复/分配/处理/确认/关闭/评价/催办） */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final KnowledgeArticleRepository articleRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public TicketService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                         TicketHistoryRepository historyRepository, UserRepository userRepository,
                         KnowledgeArticleRepository articleRepository, JdbcTemplate jdbc,
                         AuditService auditService) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /** 分页查询工单（支持状态与关键字过滤；客户角色仅能查到本人提交的工单） */
    public PageResult<Map<String, Object>> list(int page, int pageSize, String status, String keyword) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new java.util.ArrayList<>();
        // 资源级权限：客户只能看自己的工单；客服在「工单管理」只看归自己负责的工单
        // （全站/他人工单走「工单大厅」入口，见 hall 方法）
        AuthContext.UserInfo me = AuthContext.require();
        if ("CUSTOMER".equals(me.role())) {
            where.append(" AND t.customer_id = ? ");
            args.add(me.id());
        } else if ("AGENT".equals(me.role())) {
            where.append(" AND t.assignee_id = ? ");
            args.add(me.id());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.ticket_no LIKE ? OR t.title LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        // 待审核工单需要带出申请人，供主管/管理员在列表内直接审核
        String base = " FROM ticket t LEFT JOIN sys_user cu ON cu.id = t.customer_id " +
                "LEFT JOIN sys_user au ON au.id = t.assignee_id " +
                "LEFT JOIN ticket_category c ON c.id = t.category_id " +
                "LEFT JOIN (SELECT h.ticket_id, h.operator_id, h.created_at, " +
                "           ROW_NUMBER() OVER (PARTITION BY h.ticket_id ORDER BY h.id DESC) rn " +
                "           FROM ticket_history h WHERE h.action = 'APPLY') ap1 " +
                "       ON ap1.ticket_id = t.id AND ap1.rn = 1 " +
                "LEFT JOIN sys_user ap ON ap.id = ap1.operator_id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base + where, Long.class, args.toArray());
        args.add(size);
        args.add((p - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name, " +
                        "c.name AS category_name, c.color AS category_color, " +
                        "ap.id AS applicant_id, ap.display_name AS applicant_name, ap1.created_at AS apply_at" +
                        base + where + " ORDER BY t.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return PageResult.of(rows, total, p, size);
    }

    /**
     * 工单大厅：客服查看全部不归自己负责的工单（含未分配、他人处理中），
     * 未分配的待分配工单可申请认领；同时带出当前申请人便于展示"谁在申请中"
     */
    public PageResult<Map<String, Object>> hall(int page, int pageSize, String status, String keyword) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        AuthContext.UserInfo me = AuthContext.require();
        // 只排除自己已负责的工单（那些在「工单管理」里），其余全部可见
        StringBuilder where = new StringBuilder(" WHERE (t.assignee_id IS NULL OR t.assignee_id <> ?) ");
        List<Object> args = new java.util.ArrayList<>();
        args.add(me.id());
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.ticket_no LIKE ? OR t.title LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        // 申请人取该工单最新一条 APPLY 历史（同一时间只允许一个待审核申请）
        String base = " FROM ticket t " +
                "LEFT JOIN sys_user cu ON cu.id = t.customer_id " +
                "LEFT JOIN sys_user au ON au.id = t.assignee_id " +
                "LEFT JOIN ticket_category c ON c.id = t.category_id " +
                "LEFT JOIN (SELECT h.ticket_id, h.operator_id, h.created_at, " +
                "           ROW_NUMBER() OVER (PARTITION BY h.ticket_id ORDER BY h.id DESC) rn " +
                "           FROM ticket_history h WHERE h.action = 'APPLY') ap1 " +
                "       ON ap1.ticket_id = t.id AND ap1.rn = 1 " +
                "LEFT JOIN sys_user ap ON ap.id = ap1.operator_id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base + where, Long.class, args.toArray());
        args.add(size);
        args.add((p - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name, " +
                        "c.name AS category_name, c.color AS category_color, " +
                        "ap.id AS applicant_id, ap.display_name AS applicant_name, ap1.created_at AS apply_at" +
                        base + where + " ORDER BY t.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        // 申请资格：未分配且仍处于待分配状态（他人申请中的工单不可重复申请）
        for (Map<String, Object> row : rows) {
            row.put("canApply", col(row, "assignee_id") == null && "PENDING".equals(col(row, "status")));
        }
        return PageResult.of(rows, total, p, size);
    }

    /** 客服申请负责处理未分配工单：状态转为待审核，并通知主管与管理员审核 */
    @Transactional
    public void apply(Long ticketId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (t.getAssigneeId() != null) {
            throw new BizException("该工单已有处理人，无法申请");
        }
        if (!"PENDING".equals(t.getStatus())) {
            throw new BizException("该工单当前状态不可申请");
        }
        jdbc.update("UPDATE ticket SET status = 'PENDING_REVIEW' WHERE id = ?", ticketId);
        historyRepository.save(history(ticketId, u.id(), "APPLY", "PENDING", "PENDING_REVIEW", "申请负责处理该工单"));
        auditService.ok("APPLY_TICKET", "TICKET", t.getTicketNo(),
                "申请处理工单「" + t.getTitle() + "」");
        for (Long uid : reviewerIds()) {
            addNotification(uid, "REVIEW", "工单申请待审核",
                    "客服「" + u.displayName() + "」申请处理工单 " + t.getTicketNo() + "「" + t.getTitle() + "」，请审核", ticketId);
        }
        // 申请人自己也留一条待审核提醒，便于在通知中心跟踪自己申请的进度
        addNotification(u.id(), "REVIEW_APPLIED", "申请已提交，等待审核",
                "您已申请处理工单 " + t.getTicketNo() + "「" + t.getTitle() + "」，等待主管或管理员审核", ticketId);
    }

    /** 主管/管理员审核接单申请：通过则工单归申请人，驳回则退回待分配 */
    @Transactional
    public void review(Long ticketId, boolean approve, String remark) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"PENDING_REVIEW".equals(t.getStatus())) {
            throw new BizException("该工单不在待审核状态");
        }
        Map<String, Object> ap = jdbc.queryForList(
                "SELECT h.operator_id FROM ticket_history h WHERE h.ticket_id = ? AND h.action = 'APPLY' " +
                        "ORDER BY h.id DESC LIMIT 1", ticketId)
                .stream().findFirst().orElseThrow(() -> new BizException("未找到该工单的申请记录"));
        Object applicantObj = col(ap, "operator_id");
        if (applicantObj == null) {
            throw new BizException("未找到该工单的申请人");
        }
        Long applicantId = ((Number) applicantObj).longValue();
        String note = (remark == null || remark.isBlank()) ? "" : "：" + remark;
        if (approve) {
            ticketRepository.updateStatusAndAssignee(ticketId, "ASSIGNED", applicantId);
            historyRepository.save(history(ticketId, u.id(), "REVIEW", "PENDING_REVIEW", "ASSIGNED", "审核通过，工单分配给申请人" + note));
            addNotification(applicantId, "ASSIGN", "新指派工单",
                    "工单 " + t.getTicketNo() + "「" + t.getTitle() + "」已分配给您，请及时处理", ticketId);
        } else {
            jdbc.update("UPDATE ticket SET status = 'PENDING' WHERE id = ?", ticketId);
            historyRepository.save(history(ticketId, u.id(), "REVIEW", "PENDING_REVIEW", "PENDING", "审核驳回" + note));
            addNotification(applicantId, "REVIEW_RESULT", "工单申请被驳回",
                    "您申请处理的工单 " + t.getTicketNo() + "「" + t.getTitle() + "」未通过" + note, ticketId);
        }
        // 同一条待审核通知发给了多个审核人：已处理的那几位要把通知置为已读并改写文案，避免重复审核
        // 兼容历史数据：早期待审核通知的 type 还是 TICKET，按标题一起处理
        jdbc.update("UPDATE notification SET is_read = 1, title = ?, content = ? " +
                        "WHERE ticket_id = ? AND (type = 'REVIEW' OR title = '工单申请待审核') " +
                        "AND recipient_id <> ? AND is_read = 0",
                "工单申请已处理",
                "工单 " + t.getTicketNo() + " 已由「" + u.displayName() + "」" + (approve ? "审核通过" : "驳回"),
                ticketId, u.id());
        // 申请人那条「等待审核」提醒随之置为已读（结果由「新指派 / 申请被驳回」通知承载）
        jdbc.update("UPDATE notification SET is_read = 1 WHERE ticket_id = ? AND type = 'REVIEW_APPLIED' AND is_read = 0", ticketId);
        auditService.ok("REVIEW_TICKET", "TICKET", t.getTicketNo(),
                (approve ? "审核通过，工单分配给申请人" : "审核驳回，退回待分配")
                        + (remark == null || remark.isBlank() ? "" : "，备注：" + remark));
    }

    /** 待审核工单数量（仅主管/管理员使用，用于工单管理页与导航提示） */
    public long pendingReviewCount() {
        AuthContext.UserInfo me = AuthContext.require();
        if (!"ADMIN".equals(me.role()) && !"SUPERVISOR".equals(me.role())) {
            return 0;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE status = 'PENDING_REVIEW'", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 工单详情（含消息与历史，客户无权看他人工单且过滤内部备注）。
     * 注意：联表扩展字段（客户信息/分类名/发送人等）必须用 JdbcTemplate 以 Map 返回——
     * Spring Data JDBC 的实体映射会丢弃 @Transient 字段，导致详情页拿不到客户姓名、
     * 分类名等信息，与列表页（JdbcTemplate）数据不一致。
     */
    public Map<String, Object> detail(Long id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("工单不存在"));
        AuthContext.UserInfo u = AuthContext.require();
        if ("CUSTOMER".equals(u.role()) && !t.getCustomerId().equals(u.id())) {
            throw BizException.forbidden("无权访问该工单");
        }
        boolean isCustomer = "CUSTOMER".equals(u.role());
        // 工单主体：与列表页同一套联表查询，保证详情与列表数据同源一致；附带客户头像字段供概览展示
        Map<String, Object> ticket = jdbc.queryForList(
                "SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name, " +
                        "cu.phone AS customer_phone, cu.email AS customer_email, " +
                        "cu.company AS customer_company, cu.department AS customer_department, " +
                        "cu.avatar_url AS customer_avatar_url, cu.avatar_color AS customer_avatar_color, " +
                        "c.name AS category_name, c.color AS category_color " +
                        "FROM ticket t LEFT JOIN sys_user cu ON cu.id = t.customer_id " +
                        "LEFT JOIN sys_user au ON au.id = t.assignee_id " +
                        "LEFT JOIN ticket_category c ON c.id = t.category_id WHERE t.id = ?", id)
                .stream().findFirst()
                .orElseThrow(() -> BizException.notFound("工单不存在"));
        // 消息：联表带发送人姓名/角色/头像；客户与非处理人客服过滤内部备注
        List<Map<String, Object>> messages = jdbc.queryForList(
                "SELECT m.*, u.display_name AS sender_name, u.role AS sender_role, " +
                        "u.avatar_color AS sender_avatar_color, u.avatar_url AS sender_avatar_url " +
                        "FROM ticket_message m JOIN sys_user u ON u.id = m.sender_id " +
                        "WHERE m.ticket_id = ? ORDER BY m.created_at ASC, m.id ASC", id);
        boolean isAssignee = t.getAssigneeId() != null && t.getAssigneeId().equals(u.id());
        // 内部备注仅本工单处理人与主管/管理员可见，客服在工单大厅查看他人工单时只返回公开消息
        if (isCustomer || ("AGENT".equals(u.role()) && !isAssignee)) {
            messages = messages.stream()
                    .filter(m -> "PUBLIC".equals(col(m, "message_type")))
                    .toList();
        }
        // 历史：联表带操作人姓名，供时间线展示"谁做了什么"
        List<Map<String, Object>> histories = jdbc.queryForList(
                "SELECT h.*, u.display_name AS operator_name, u.role AS operator_role " +
                        "FROM ticket_history h LEFT JOIN sys_user u ON u.id = h.operator_id " +
                        "WHERE h.ticket_id = ? ORDER BY h.created_at ASC, h.id ASC", id);

        Map<String, Object> data = new HashMap<>();
        data.put("ticket", ticket);
        data.put("messages", messages);
        data.put("histories", histories);
        // 待审核工单：带出最近一次申请，供主管/管理员在详情页审核时展示申请人
        List<Map<String, Object>> applies = jdbc.queryForList(
                "SELECT h.operator_id AS applicant_id, u.display_name AS applicant_name, " +
                        "h.created_at AS apply_at, h.remark AS apply_remark " +
                        "FROM ticket_history h LEFT JOIN sys_user u ON u.id = h.operator_id " +
                        "WHERE h.ticket_id = ? AND h.action = 'APPLY' ORDER BY h.id DESC LIMIT 1", id);
        if (!applies.isEmpty()) {
            data.put("apply", applies.get(0));
        }
        // 详情页左侧客户卡：历史工单数用于判定活跃度标签
        data.put("customerTicketCount", customerTicketCount(t.getCustomerId()));
        // 详情页右栏：按工单标题与分类匹配的推荐知识（带匹配度百分比）
        data.put("relatedKnowledge", relatedKnowledge(t.getTitle(), (String) col(ticket, "category_name")));
        return data;
    }

    /** 按列名取 Map 中的值（兼容大小写差异，MySQL 列名一般为小写） */
    private static Object col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 统计客户历史工单数（用于详情页客户活跃度标签） */
    private long customerTicketCount(Long customerId) {
        if (customerId == null) {
            return 0L;
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE customer_id = ?", Long.class, customerId);
        return n == null ? 0L : n;
    }

    /** 按工单标题与分类检索相关文章，用 bigram 重合度换算匹配百分比（启发式，非语义向量） */
    private List<Map<String, Object>> relatedKnowledge(String title, String categoryName) {
        StringBuilder query = new StringBuilder();
        if (title != null) {
            query.append(title).append(' ');
        }
        if (categoryName != null) {
            query.append(categoryName);
        }
        Set<String> grams = bigrams(query.toString(), 24, 16);
        if (grams.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeArticle> docs = new LinkedHashMap<>();
        Map<Long, Integer> hits = new HashMap<>();
        for (String g : grams) {
            for (KnowledgeArticle a : articleRepository.search(g, 8)) {
                docs.putIfAbsent(a.getId(), a);
                hits.merge(a.getId(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        docs.values().forEach(a -> {
            int hit = hits.getOrDefault(a.getId(), 0);
            // 命中率开方放大：命中 1/4 约 50%，命中 4/4 约 99%，避免长标题下分数过低
            int score = (int) Math.round(Math.sqrt(hit / (double) grams.size()) * 100);
            if (score < 15) {
                return;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("summary", a.getSummary());
            m.put("categoryName", a.getCategoryName());
            m.put("viewCount", a.getViewCount() == null ? 0 : a.getViewCount());
            m.put("matchScore", Math.min(score, 99));
            result.add(m);
        });
        result.sort((x, y) -> (int) y.get("matchScore") - (int) x.get("matchScore"));
        return result.size() > 3 ? result.subList(0, 3) : result;
    }

    /** 把文本切成去重后的二字片段，最多取前 limit 个字、maxGrams 个片段 */
    private Set<String> bigrams(String text, int limit, int maxGrams) {
        String clean = text == null ? "" : text.replaceAll("[\\s，。！？、,.!?：:；;（）()\\[\\]【】\"']", "");
        if (clean.length() > limit) {
            clean = clean.substring(0, limit);
        }
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 1 < clean.length() && grams.size() < maxGrams; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }

    /** 创建工单（客户提交，初始状态 PENDING） */
    @Transactional
    public Map<String, Object> create(String title, String description, Long categoryId, String priority) {
        AuthContext.UserInfo u = AuthContext.require();
        if (title == null || title.isBlank()) {
            throw new BizException("工单主题不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new BizException("问题描述不能为空");
        }
        if (priority == null) {
            priority = "MEDIUM";
        }
        String ticketNo = "ISD" + (System.currentTimeMillis() / 1000) + String.format("%04d", (int) (Math.random() * 10000));
        LocalDateTime now = LocalDateTime.now();
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setTitle(title.trim());
        t.setDescription(description);
        t.setCustomerId(u.id());
        t.setCategoryId(categoryId);
        t.setPriority(priority);
        t.setStatus("PENDING");
        t.setSource("WEB");
        t.setSlaResponseAt(now.plusHours(2));
        t.setSlaResolveAt(now.plusHours(24));
        t.setReopenCount(0);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        Ticket saved = ticketRepository.save(t);

        historyRepository.save(history(saved.getId(), u.id(), "CREATE", null, "PENDING", "客户创建工单"));
        auditService.ok("CREATE_TICKET", "TICKET", saved.getTicketNo(),
                "创建工单「" + saved.getTitle() + "」，优先级 " + priority);
        return Map.of("id", saved.getId(), "ticketNo", saved.getTicketNo());
    }

    /** 回复工单（公开或内部备注，客服首次公开回复记录响应时间） */
    @Transactional
    public void reply(Long ticketId, String content, String messageType) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (content == null || content.isBlank()) {
            throw new BizException("回复内容不能为空");
        }
        if (!"PUBLIC".equals(messageType) && !"INTERNAL".equals(messageType)) {
            throw new BizException("非法消息类型");
        }
        if ("INTERNAL".equals(messageType) && "CUSTOMER".equals(u.role())) {
            throw BizException.forbidden("客户不能发送内部备注");
        }
        TicketMessage m = new TicketMessage();
        m.setTicketId(ticketId);
        m.setSenderId(u.id());
        m.setMessageType(messageType);
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageRepository.save(m);

        // 客服首次公开回复记录首次响应时间
        if ("PUBLIC".equals(messageType) && !"CUSTOMER".equals(u.role()) && t.getFirstResponseAt() == null) {
            jdbc.update("UPDATE ticket SET first_response_at = ? WHERE id = ?", LocalDateTime.now(), ticketId);
        }
        auditService.ok("REPLY_TICKET", "TICKET", t.getTicketNo(),
                ("INTERNAL".equals(messageType) ? "添加内部备注：" : "回复工单：") + truncateText(content, 120));
    }

    /** 文本截断到指定长度，超出补省略号（日志详情用） */
    private static String truncateText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** 分配/转派工单给客服 */
    @Transactional
    public void assign(Long ticketId, Long assigneeId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (assigneeId == null) {
            throw new BizException("请选择处理人");
        }
        String old = String.valueOf(t.getAssigneeId());
        ticketRepository.updateStatusAndAssignee(ticketId, "ASSIGNED", assigneeId);
        historyRepository.save(history(ticketId, u.id(), "ASSIGN", old, String.valueOf(assigneeId), "分配/转派工单"));
        addNotification(assigneeId, "工单分配", "您有一个新的工单待处理", ticketId);
        auditService.ok("ASSIGN_TICKET", "TICKET", t.getTicketNo(), "分配/转派工单给 " + assigneeName(assigneeId));
    }

    /** 查询用户显示名，查不到时回落到 ID 文本（审计日志用） */
    private String assigneeName(Long userId) {
        try {
            String name = jdbc.queryForObject("SELECT display_name FROM sys_user WHERE id = ?", String.class, userId);
            return name == null ? String.valueOf(userId) : name;
        } catch (Exception e) {
            return String.valueOf(userId);
        }
    }

    /** 客服接单（仅分配给自己） */
    @Transactional
    public void accept(Long ticketId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (t.getAssigneeId() == null || u.id() != t.getAssigneeId()) {
            throw BizException.forbidden("只能受理分配给自己的工单");
        }
        ticketRepository.updateStatus(ticketId, "PROCESSING");
        historyRepository.save(history(ticketId, u.id(), "ACCEPT", "ASSIGNED", "PROCESSING", "客服接单"));
        auditService.ok("ACCEPT_TICKET", "TICKET", t.getTicketNo(), "受理工单「" + t.getTitle() + "」");
    }

    /** 提交解决方案（工单进入待确认） */
    @Transactional
    public void resolve(Long ticketId, String solution) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (solution == null || solution.isBlank()) {
            throw new BizException("解决方案不能为空");
        }
        jdbc.update("UPDATE ticket SET status = 'WAITING', solution = ? WHERE id = ?", solution, ticketId);
        historyRepository.save(history(ticketId, u.id(), "RESOLVE", t.getStatus(), "WAITING", "提交解决方案"));
        addNotification(t.getCustomerId(), "工单待确认", "您的工单已有解决方案，请确认", ticketId);
        auditService.ok("RESOLVE_TICKET", "TICKET", t.getTicketNo(),
                "提交解决方案：" + truncateText(solution, 120));
    }

    /** 客户确认解决方案（已解决则关闭，未解决则重开） */
    @Transactional
    public void confirm(Long ticketId, boolean solved, String feedback) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"CUSTOMER".equals(u.role()) || !t.getCustomerId().equals(u.id())) {
            throw BizException.forbidden("只有工单创建者可确认");
        }
        if (!"WAITING".equals(t.getStatus())) {
            throw new BizException("当前状态不可确认");
        }
        if (solved) {
            jdbc.update("UPDATE ticket SET status = 'RESOLVED', resolved_at = ?, feedback = ? WHERE id = ?",
                    LocalDateTime.now(), feedback, ticketId);
            historyRepository.save(history(ticketId, u.id(), "CONFIRM", "WAITING", "RESOLVED", "客户确认已解决"));
        } else {
            jdbc.update("UPDATE ticket SET status = 'PROCESSING' WHERE id = ?", ticketId);
            historyRepository.save(history(ticketId, u.id(), "REOPEN", "WAITING", "PROCESSING", "客户反馈未解决：" + feedback));
        }
        auditService.ok("CONFIRM_TICKET", "TICKET", t.getTicketNo(),
                solved ? "客户确认已解决" : "客户反馈未解决：" + truncateText(feedback, 120));
    }

    /** 关闭已解决工单 */
    @Transactional
    public void close(Long ticketId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"RESOLVED".equals(t.getStatus())) {
            throw new BizException("只有已解决工单可关闭");
        }
        jdbc.update("UPDATE ticket SET status = 'CLOSED', closed_at = ? WHERE id = ?", LocalDateTime.now(), ticketId);
        historyRepository.save(history(ticketId, u.id(), "CLOSE", "RESOLVED", "CLOSED", "关闭工单"));
        auditService.ok("CLOSE_TICKET", "TICKET", t.getTicketNo(), "关闭工单「" + t.getTitle() + "」");
    }

    /** 重新打开已关闭工单 */
    @Transactional
    public void reopen(Long ticketId, String reason) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"CLOSED".equals(t.getStatus())) {
            throw new BizException("只有已关闭工单可重新打开");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("请填写重新打开原因");
        }
        int cnt = t.getReopenCount() == null ? 1 : t.getReopenCount() + 1;
        jdbc.update("UPDATE ticket SET status = 'PROCESSING', reopen_count = ? WHERE id = ?", cnt, ticketId);
        historyRepository.save(history(ticketId, u.id(), "REOPEN", "CLOSED", "PROCESSING", "重新打开：" + reason));
        auditService.ok("REOPEN_TICKET", "TICKET", t.getTicketNo(), "重新打开工单：" + truncateText(reason, 120));
    }

    /** 客户评价工单（1-5 分，不可重复） */
    @Transactional
    public void rate(Long ticketId, int rating, String comment) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"CUSTOMER".equals(u.role()) || !t.getCustomerId().equals(u.id())) {
            throw BizException.forbidden("只有工单创建者可评价");
        }
        if (rating < 1 || rating > 5) {
            throw new BizException("评分范围 1-5");
        }
        if (t.getSatisfaction() != null) {
            throw new BizException("已评价，不可重复提交");
        }
        jdbc.update("UPDATE ticket SET satisfaction = ?, feedback = ? WHERE id = ?", rating, comment, ticketId);
        historyRepository.save(history(ticketId, u.id(), "RATE", null, String.valueOf(rating), "客户评价"));
        auditService.ok("RATE_TICKET", "TICKET", t.getTicketNo(), "客户评价 " + rating + " 星");
    }

    /** 客户催办未结束工单，通知处理人 */
    @Transactional
    public void urge(Long ticketId, String reason) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        // 只有工单创建者且工单未到终态时可催办
        if (!"CUSTOMER".equals(u.role()) || !t.getCustomerId().equals(u.id())) {
            throw BizException.forbidden("只有工单创建者可催办");
        }
        if ("RESOLVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus())) {
            throw new BizException("工单已结束，无需催办");
        }
        historyRepository.save(history(ticketId, u.id(), "URGE", null, t.getStatus(),
                "客户催办" + (reason == null || reason.isBlank() ? "" : "：" + reason)));
        // 通知处理人（未分配时通知所有客服/主管）
        if (t.getAssigneeId() != null) {
            addNotification(t.getAssigneeId(), "工单催办", "您有工单被客户催办，请尽快处理", ticketId);
        }
        auditService.ok("URGE_TICKET", "TICKET", t.getTicketNo(),
                "客户催办" + (reason == null || reason.isBlank() ? "" : "：" + truncateText(reason, 100)));
    }

    /** 查询可分配的客服列表 */
    public List<Map<String, Object>> agents() {
        return jdbc.queryForList("SELECT id, display_name AS name, department, avatar_color FROM sys_user WHERE role='AGENT' AND status=1 ORDER BY id");
    }

    /** 查询参与接单申请审核的人员（主管与管理员） */
    private List<Long> reviewerIds() {
        return jdbc.queryForList("SELECT id FROM sys_user WHERE role IN ('ADMIN','SUPERVISOR') AND status = 1", Long.class);
    }

    /** 查询启用的工单分类列表 */
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, color, sla_hours FROM ticket_category WHERE status=1 ORDER BY sort");
    }

    /** 按 id 取工单，不存在则抛异常 */
    private Ticket requireTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> BizException.notFound("工单不存在"));
    }

    /** 构造工单历史记录对象 */
    private TicketHistory history(Long ticketId, Long operatorId, String action, String oldValue, String newValue, String remark) {
        TicketHistory h = new TicketHistory();
        h.setTicketId(ticketId);
        h.setOperatorId(operatorId);
        h.setAction(action);
        h.setOldValue(oldValue);
        h.setNewValue(newValue);
        h.setRemark(remark);
        h.setCreatedAt(LocalDateTime.now());
        return h;
    }

    /** 给指定用户发站内通知（失败忽略） */
    private void addNotification(Long recipientId, String title, String content, Long ticketId) {
        addNotification(recipientId, "TICKET", title, content, ticketId);
    }

    /** 给指定用户发带类型的站内通知（REVIEW 待审核 / ASSIGN 新指派 / REVIEW_RESULT 审核结果） */
    private void addNotification(Long recipientId, String type, String title, String content, Long ticketId) {
        try {
            jdbc.update("INSERT INTO notification(recipient_id,type,title,content,ticket_id,is_read,created_at) VALUES(?,?,?,?,?,0,?)",
                    recipientId, type, title, content, ticketId, LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }
}
