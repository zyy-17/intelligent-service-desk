package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table
public class AiTaskLog {
    @Id
    private Long id;
    private String requestId;
    private String taskType;
    private String status;
    private Integer durationMs;
    private String question;
    private Integer answerLength;
    private String model;
    private String source;
    private String errorSummary;
    private LocalDateTime createdAt;
}
