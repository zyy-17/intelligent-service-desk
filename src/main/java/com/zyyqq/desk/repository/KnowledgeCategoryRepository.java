package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.KnowledgeCategory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeCategoryRepository extends CrudRepository<KnowledgeCategory, Long> {

    @Query("SELECT * FROM knowledge_category ORDER BY sort, id")
    List<KnowledgeCategory> findAllOrdered();

    @Query("SELECT * FROM knowledge_category WHERE id = :id")
    java.util.Optional<KnowledgeCategory> findById2(@Param("id") Long id);
}
