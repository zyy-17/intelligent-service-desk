package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.OperationLog;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 操作日志数据访问接口 */
public interface OperationLogRepository extends CrudRepository<OperationLog, Long> {

    /** 分页查询操作日志 */
    @Query("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<OperationLog> findPage(@Param("limit") int limit, @Param("offset") int offset);

    /** 统计操作日志总数 */
    @Query("SELECT COUNT(*) FROM operation_log")
    long countAll();
}
