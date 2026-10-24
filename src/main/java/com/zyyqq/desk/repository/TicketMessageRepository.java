package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.TicketMessage;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketMessageRepository extends CrudRepository<TicketMessage, Long> {

    @Query("""
            SELECT m.*, u.display_name AS sender_name, u.role AS sender_role, u.avatar_color AS avatar_color
            FROM ticket_message m
            JOIN sys_user u ON u.id = m.sender_id
            WHERE m.ticket_id = :ticketId
            ORDER BY m.created_at ASC, m.id ASC
            """)
    List<TicketMessage> findByTicket(@Param("ticketId") Long ticketId);
}
