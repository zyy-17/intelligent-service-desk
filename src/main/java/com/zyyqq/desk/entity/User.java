package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 用户实体（对应 sys_user 表） */
@Data
@Table("sys_user")
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
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
