package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.Ticket;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 工单数据访问接口 */
public interface TicketRepository extends CrudRepository<Ticket, Long> {

    /** 按工单号查询工单 */
    @Query("SELECT * FROM ticket WHERE ticket_no = :no")
    Optional<Ticket> findByTicketNo(@Param("no") String no);

    /** 分页查询工单（联表带客户/客服/分类名） */
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

    /** 统计工单总数 */
    @Query("SELECT COUNT(*) FROM ticket")
    long countAll();

    /** 按状态统计工单数 */
    @Query("SELECT COUNT(*) FROM ticket WHERE status = :status")
    long countByStatus(@Param("status") String status);

    /** 按优先级统计工单数 */
    @Query("SELECT COUNT(*) FROM ticket WHERE priority = :priority")
    long countByPriority(@Param("priority") String priority);

    /** 查询工单详情（联表带客户联系方式/公司部门，供详情页左侧客户卡展示） */
    @Query("""
            SELECT t.*, cu.display_name AS customer_name, au.display_name AS assignee_name,
                   cu.phone AS customer_phone, cu.email AS customer_email,
                   cu.company AS customer_company, cu.department AS customer_department,
                   c.name AS category_name, c.color AS category_color
            FROM ticket t
            LEFT JOIN sys_user cu ON cu.id = t.customer_id
            LEFT JOIN sys_user au ON au.id = t.assignee_id
            LEFT JOIN ticket_category c ON c.id = t.category_id
            WHERE t.id = :id
            """)
    Optional<Ticket> findDetail(@Param("id") Long id);

    /** 更新工单状态与处理人 */
    @Modifying
    @Query("UPDATE ticket SET status = :status, assignee_id = :assigneeId WHERE id = :id")
    void updateStatusAndAssignee(@Param("id") Long id, @Param("status") String status,
                                 @Param("assigneeId") Long assigneeId);

    /** 更新工单状态 */
    @Modifying
    @Query("UPDATE ticket SET status = :status WHERE id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
