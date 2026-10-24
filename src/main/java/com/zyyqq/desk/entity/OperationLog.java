package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
public class OperationLog {
    @Id
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
