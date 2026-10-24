package com.zyyqq.desk.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
public class KnowledgeCategory {
    @Id
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
}
