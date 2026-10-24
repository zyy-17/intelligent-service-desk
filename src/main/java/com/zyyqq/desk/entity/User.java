package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
public class User {
    @Id
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private String phone;
    private String email;
    private String company;
    private String department;
    private String avatarColor;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
