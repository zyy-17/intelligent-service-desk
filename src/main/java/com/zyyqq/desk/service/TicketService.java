package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.entity.Ticket;
import com.zyyqq.desk.entity.TicketHistory;
import com.zyyqq.desk.entity.TicketMessage;
import com.zyyqq.desk.repository.TicketHistoryRepository;
import com.zyyqq.desk.repository.TicketMessageRepository;
import com.zyyqq.desk.repository.TicketRepository;
import com.zyyqq.desk.repository.UserRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;

    public TicketService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                         TicketHistoryRepository historyRepository, UserRepository userRepository, JdbcTemplate jdbc) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    public PageResult<Map<String, Object>> list(int page, int pageSize, String status, String keyword) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.ticket_no LIKE ? OR t.title LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        String base = " FROM ticket t LEFT JOIN sys_user cu ON cu.id = t.customer_id " +
                "LEFT JOIN sys_user au ON au.id = t.assignee_id " +
                "LEFT JOIN ticket_category c ON c.id = t.category_id ";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base + where, Long.class, args.toArray());
        args.add(size);
        args.add((p - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name, " +
                        "c.name AS category_name, c.color AS category_color" + base + where +
                        " ORDER BY t.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return PageResult.of(rows, total, p, size);
    }

    public Map<String, Object> detail(Long id) {
        Ticket t = ticketRepository.findDetail(id)
                .orElseThrow(() -> BizException.notFound("工单不存在"));
        AuthContext.UserInfo u = AuthContext.require();
        if ("CUSTOMER".equals(u.role()) && !t.getCustomerId().equals(u.id())) {
            throw BizException.forbidden("无权访问该工单");
        }
        // 客户不返回内部备注
        List<TicketMessage> messages = messageRepository.findByTicket(id);
        if ("CUSTOMER".equals(u.role())) {
            messages = messages.stream().filter(m -> "PUBLIC".equals(m.getMessageType())).toList();
        }
        List<TicketHistory> histories = historyRepository.findByTicket(id);
        Map<String, Object> data = new HashMap<>();
        data.put("ticket", t);
        data.put("messages", messages);
        data.put("histories", histories);
        return data;
    }

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
        return Map.of("id", saved.getId(), "ticketNo", saved.getTicketNo());
    }

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
    }

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
    }

    @Transactional
    public void accept(Long ticketId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (t.getAssigneeId() == null || u.id() != t.getAssigneeId()) {
            throw BizException.forbidden("只能受理分配给自己的工单");
        }
        ticketRepository.updateStatus(ticketId, "PROCESSING");
        historyRepository.save(history(ticketId, u.id(), "ACCEPT", "ASSIGNED", "PROCESSING", "客服接单"));
    }

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
    }

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
    }

    @Transactional
    public void close(Long ticketId) {
        AuthContext.UserInfo u = AuthContext.require();
        Ticket t = requireTicket(ticketId);
        if (!"RESOLVED".equals(t.getStatus())) {
            throw new BizException("只有已解决工单可关闭");
        }
        jdbc.update("UPDATE ticket SET status = 'CLOSED', closed_at = ? WHERE id = ?", LocalDateTime.now(), ticketId);
        historyRepository.save(history(ticketId, u.id(), "CLOSE", "RESOLVED", "CLOSED", "关闭工单"));
    }

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
    }

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
    }

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
    }

    public List<Map<String, Object>> agents() {
        return jdbc.queryForList("SELECT id, display_name AS name, department, avatar_color FROM sys_user WHERE role='AGENT' AND status=1 ORDER BY id");
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, color, sla_hours FROM ticket_category WHERE status=1 ORDER BY sort");
    }

    private Ticket requireTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> BizException.notFound("工单不存在"));
    }

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

    private void addNotification(Long recipientId, String title, String content, Long ticketId) {
        try {
            jdbc.update("INSERT INTO notification(recipient_id,type,title,content,ticket_id,is_read,created_at) VALUES(?,?,?,?,?,0,?)",
                    recipientId, "TICKET", title, content, ticketId, LocalDateTime.now());
        } catch (Exception ignored) {
        }
    }
}
