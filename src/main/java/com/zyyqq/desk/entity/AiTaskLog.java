package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** AI 调用日志实体（对应 ai_task_log 表） */
@Data
@Table
public class AiTaskLog {
    @Id
    private Long id;
    private String requestId;
    private String taskType;
    private Long userId;
    private String userName;
    private String userRole;
    private String ticketNo;
    private String status;
    private Integer durationMs;
    private String question;
    private String answer;
    private Integer answerLength;
    private String model;
    private String source;
    private String errorSummary;
    private LocalDateTime createdAt;
}
