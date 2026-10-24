package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.TicketHistory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketHistoryRepository extends CrudRepository<TicketHistory, Long> {

    @Query("""
            SELECT h.*, u.display_name AS operator_name
            FROM ticket_history h
            LEFT JOIN sys_user u ON u.id = h.operator_id
            WHERE h.ticket_id = :ticketId
            ORDER BY h.created_at DESC, h.id DESC
            """)
    List<TicketHistory> findByTicket(@Param("ticketId") Long ticketId);
}
