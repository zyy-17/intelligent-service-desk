package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table
public class KnowledgeCategory {
    @Id
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
}
