package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.entity.KnowledgeCategory;
import com.zyyqq.desk.repository.KnowledgeArticleRepository;
import com.zyyqq.desk.repository.KnowledgeCategoryRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 知识库服务：文章与分类的增删改查、热门与检索、缓存一致性 */
@Service
public class KnowledgeService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final RedisSupport redisSupport;
    private final AppProperties props;

    public KnowledgeService(KnowledgeArticleRepository articleRepository, KnowledgeCategoryRepository categoryRepository,
                            RedisSupport redisSupport, AppProperties props) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.redisSupport = redisSupport;
        this.props = props;
    }

    /** 查询知识分类列表（带缓存） */
    public List<KnowledgeCategory> categories() {
        return redisSupport.cacheJson("knowledge_categories", List.class,
                Duration.ofSeconds(props.getRedis().getCategoriesTtl()),
                categoryRepository::findAllOrdered);
    }

    /** 分页查询文章（支持关键字检索） */
    public PageResult<KnowledgeArticle> list(int page, int pageSize, String keyword) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        if (keyword != null && !keyword.isBlank()) {
            // 简化：走搜索逻辑返回已发布
            List<KnowledgeArticle> hits = articleRepository.search(keyword, 100);
            return PageResult.of(hits, hits.size(), 1, hits.size());
        }
        List<KnowledgeArticle> list = articleRepository.findList(size, (p - 1) * size);
        long total = articleRepository.countAll();
        return PageResult.of(list, total, p, size);
    }

    /** 文章详情（客户仅能看已发布，浏览数自增） */
    public KnowledgeArticle detail(Long id) {
        KnowledgeArticle a = articleRepository.findDetail(id)
                .orElseThrow(() -> BizException.notFound("文章不存在"));
        AuthContext.UserInfo u = AuthContext.require();
        if (!"PUBLISHED".equals(a.getStatus()) && "CUSTOMER".equals(u.role())) {
            throw BizException.forbidden("文章未发布");
        }
        articleRepository.incrView(id);
        return a;
    }

    /** 热门文章（带缓存） */
    public List<KnowledgeArticle> hot(int limit) {
        return redisSupport.cacheJson("hot_articles_" + limit, List.class,
                Duration.ofSeconds(props.getRedis().getHotArticlesTtl()),
                () -> articleRepository.findHot(limit));
    }

    /** 关键字检索文章 */
    public List<KnowledgeArticle> search(String kw, int limit) {
        return articleRepository.search(kw, Math.min(Math.max(limit, 1), 50));
    }

    /** 新增或更新文章，并主动失效相关缓存 */
    public KnowledgeArticle save(Map<String, Object> body) {
        AuthContext.UserInfo u = AuthContext.require();
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        String title = String.valueOf(body.getOrDefault("title", ""));
        if (title.isBlank()) {
            throw new BizException("标题不能为空");
        }
        KnowledgeArticle a = new KnowledgeArticle();
        a.setId(id);
        a.setTitle(title);
        a.setSummary(String.valueOf(body.getOrDefault("summary", "")));
        a.setBody(String.valueOf(body.getOrDefault("body", "")));
        a.setCategoryId(body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId"))));
        a.setKeywords(String.valueOf(body.getOrDefault("keywords", "")));
        a.setStatus(String.valueOf(body.getOrDefault("status", "DRAFT")));
        a.setAuthorId(u.id());
        if (a.getStatus() == null || a.getStatus().isBlank()) {
            a.setStatus("DRAFT");
        }
        if ("PUBLISHED".equals(a.getStatus()) && a.getPublishedAt() == null) {
            a.setPublishedAt(java.time.LocalDateTime.now());
        }
        if (id == null) {
            a.setCreatedAt(java.time.LocalDateTime.now());
        }
        a.setUpdatedAt(java.time.LocalDateTime.now());
        KnowledgeArticle saved = articleRepository.save(a);
        // 知识数据变更 → 主动失效相关缓存（缓存一致性管理）
        redisSupport.evictCache("hot_articles_6", "hot_articles_5", "hot_articles_10", "knowledge_categories");
        return saved;
    }

    /** 删除文章并失效缓存 */
    public void delete(Long id) {
        articleRepository.deleteById(id);
        redisSupport.evictCache("hot_articles_6", "hot_articles_5", "hot_articles_10", "knowledge_categories");
    }

    /** 知识库统计信息 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", articleRepository.countAll());
        return m;
    }
}
