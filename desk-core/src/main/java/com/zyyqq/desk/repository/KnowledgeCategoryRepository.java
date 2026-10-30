package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.KnowledgeCategory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 知识分类数据访问接口 */
public interface KnowledgeCategoryRepository extends CrudRepository<KnowledgeCategory, Long> {

    /** 按排序查询全部分类 */
    @Query("SELECT * FROM knowledge_category ORDER BY sort, id")
    List<KnowledgeCategory> findAllOrdered();

    /** 按 id 查询分类 */
    @Query("SELECT * FROM knowledge_category WHERE id = :id")
    java.util.Optional<KnowledgeCategory> findById2(@Param("id") Long id);
}
