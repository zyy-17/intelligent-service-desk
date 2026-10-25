package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.TicketMessage;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 工单消息数据访问接口 */
public interface TicketMessageRepository extends CrudRepository<TicketMessage, Long> {

    /** 查询某工单的消息（联表带发送人信息） */
    @Query("""
            SELECT m.*, u.display_name AS sender_name, u.role AS sender_role, u.avatar_color AS avatar_color
            FROM ticket_message m
            JOIN sys_user u ON u.id = m.sender_id
            WHERE m.ticket_id = :ticketId
            ORDER BY m.created_at ASC, m.id ASC
            """)
    List<TicketMessage> findByTicket(@Param("ticketId") Long ticketId);
}
