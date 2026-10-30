package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.AiTaskLog;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** AI 调用日志数据访问接口 */
public interface AiTaskLogRepository extends CrudRepository<AiTaskLog, Long> {

    /** 分页查询 AI 日志 */
    @Query("SELECT * FROM ai_task_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<AiTaskLog> findPage(@Param("limit") int limit, @Param("offset") int offset);

    /** 统计 AI 日志总数 */
    @Query("SELECT COUNT(*) FROM ai_task_log")
    long countAll();
}
