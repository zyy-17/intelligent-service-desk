package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.OperationLog;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OperationLogRepository extends CrudRepository<OperationLog, Long> {

    @Query("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<OperationLog> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(*) FROM operation_log")
    long countAll();
}
