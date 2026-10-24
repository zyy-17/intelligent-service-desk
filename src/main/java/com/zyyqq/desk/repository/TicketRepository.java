package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.Ticket;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends CrudRepository<Ticket, Long> {

    @Query("SELECT * FROM ticket WHERE ticket_no = :no")
    Optional<Ticket> findByTicketNo(@Param("no") String no);

    @Query("""
            SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name,
                   c.name AS category_name, c.color AS category_color
            FROM ticket t
            LEFT JOIN sys_user cu ON cu.id = t.customer_id
            LEFT JOIN sys_user au ON au.id = t.assignee_id
            LEFT JOIN ticket_category c ON c.id = t.category_id
            ORDER BY t.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Ticket> findList(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(*) FROM ticket")
    long countAll();

    @Query("SELECT COUNT(*) FROM ticket WHERE status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(*) FROM ticket WHERE priority = :priority")
    long countByPriority(@Param("priority") String priority);

    @Query("""
            SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name,
                   c.name AS category_name, c.color AS category_color
            FROM ticket t
            LEFT JOIN sys_user cu ON cu.id = t.customer_id
            LEFT JOIN sys_user au ON au.id = t.assignee_id
            LEFT JOIN ticket_category c ON c.id = t.category_id
            WHERE t.id = :id
            """)
    Optional<Ticket> findDetail(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ticket SET status = :status, assignee_id = :assigneeId WHERE id = :id")
    void updateStatusAndAssignee(@Param("id") Long id, @Param("status") String status,
                                 @Param("assigneeId") Long assigneeId);

    @Modifying
    @Query("UPDATE ticket SET status = :status WHERE id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
