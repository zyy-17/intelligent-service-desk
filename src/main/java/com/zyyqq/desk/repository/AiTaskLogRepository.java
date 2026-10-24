package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.AiTaskLog;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiTaskLogRepository extends CrudRepository<AiTaskLog, Long> {

    @Query("SELECT * FROM ai_task_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<AiTaskLog> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(*) FROM ai_task_log")
    long countAll();
}
