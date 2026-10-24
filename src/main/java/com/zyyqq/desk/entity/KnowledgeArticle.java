package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Data
public class KnowledgeArticle {
    @Id
    private Long id;
    private String title;
    private String summary;
    private String body;
    private Long categoryId;
    private String keywords;
    private String status;
    private Long authorId;
    private Integer viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 联表字段（非表字段）
    @Transient
    private String categoryName;
    @Transient
    private String authorName;
}
