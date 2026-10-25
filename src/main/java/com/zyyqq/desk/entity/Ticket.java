package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Data
@Table
public class Ticket {
    @Id
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private Long customerId;
    private Long assigneeId;
    private Long categoryId;
    private String priority;
    private String status;
    private String source;
    private String solution;
    private Integer satisfaction;
    private String feedback;
    private LocalDateTime slaResponseAt;
    private LocalDateTime slaResolveAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private Integer reopenCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 联表字段（非表字段）
    @Transient
    private String customerName;
    @Transient
    private String assigneeName;
    @Transient
    private String categoryName;
    @Transient
    private String categoryColor;
}
