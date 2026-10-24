package com.zyyqq.desk.repository;

import com.zyyqq.desk.entity.KnowledgeArticle;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeArticleRepository extends CrudRepository<KnowledgeArticle, Long> {

    @Query("""
            SELECT a.*, c.name AS category_name, u.display_name AS author_name
            FROM knowledge_article a
            LEFT JOIN knowledge_category c ON c.id = a.category_id
            LEFT JOIN sys_user u ON u.id = a.author_id
            ORDER BY a.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<KnowledgeArticle> findList(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT COUNT(*) FROM knowledge_article")
    long countAll();

    @Query("""
            SELECT a.*, c.name AS category_name, u.display_name AS author_name
            FROM knowledge_article a
            LEFT JOIN knowledge_category c ON c.id = a.category_id
            LEFT JOIN sys_user u ON u.id = a.author_id
            WHERE a.id = :id
            """)
    Optional<KnowledgeArticle> findDetail(@Param("id") Long id);

    @Query("""
            SELECT a.*, c.name AS category_name, u.display_name AS author_name
            FROM knowledge_article a
            LEFT JOIN knowledge_category c ON c.id = a.category_id
            LEFT JOIN sys_user u ON u.id = a.author_id
            WHERE a.status = 'PUBLISHED'
              AND (a.title LIKE CONCAT('%', :kw, '%') OR a.summary LIKE CONCAT('%', :kw, '%')
                   OR a.body LIKE CONCAT('%', :kw, '%') OR a.keywords LIKE CONCAT('%', :kw, '%'))
            ORDER BY a.view_count DESC, a.published_at DESC
            LIMIT :limit
            """)
    List<KnowledgeArticle> search(@Param("kw") String kw, @Param("limit") int limit);

    @Query("SELECT a.*, c.name AS category_name FROM knowledge_article a LEFT JOIN knowledge_category c ON c.id = a.category_id WHERE a.status = 'PUBLISHED' ORDER BY a.view_count DESC LIMIT :limit")
    List<KnowledgeArticle> findHot(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE knowledge_article SET view_count = view_count + 1 WHERE id = :id")
    void incrView(@Param("id") Long id);
}
