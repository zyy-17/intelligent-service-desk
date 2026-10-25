package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

/** 工单历史实体（对应 ticket_history 表） */
@Data
@Table
public class TicketHistory {
    @Id
    private Long id;
    private Long ticketId;
    private Long operatorId;
    private String action;
    private String oldValue;
    private String newValue;
    private String remark;
    private LocalDateTime createdAt;

    @Transient
    private String operatorName;
}
