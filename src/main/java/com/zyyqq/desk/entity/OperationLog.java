package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 操作日志实体（对应 operation_log 表，查询侧走 JdbcTemplate 联表 Map） */
@Data
@Table
public class OperationLog {
    @Id
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String role;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String status;
    private String ip;
    private LocalDateTime createdAt;
}
