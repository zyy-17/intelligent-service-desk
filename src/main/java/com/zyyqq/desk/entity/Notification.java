package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
public class Notification {
    @Id
    private Long id;
    private Long recipientId;
    private String type;
    private String title;
    private String content;
    private Long ticketId;
    private Integer isRead;
    private LocalDateTime createdAt;
}
