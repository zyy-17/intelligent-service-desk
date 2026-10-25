package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Data
@Table
public class TicketMessage {
    @Id
    private Long id;
    private Long ticketId;
    private Long senderId;
    private String messageType;
    private String content;
    private LocalDateTime createdAt;

    @Transient
    private String senderName;
    @Transient
    private String senderRole;
    @Transient
    private String avatarColor;
}
