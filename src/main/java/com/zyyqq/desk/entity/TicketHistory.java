package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Data
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
