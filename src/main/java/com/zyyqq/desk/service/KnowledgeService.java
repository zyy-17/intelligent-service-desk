package com.zyyqq.desk.service;

import com.zyyqq.desk.common.BizException;
import com.zyyqq.desk.common.PageResult;
import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.entity.KnowledgeArticle;
import com.zyyqq.desk.entity.KnowledgeCategory;
import com.zyyqq.desk.repository.KnowledgeArticleRepository;
import com.zyyqq.desk.repository.KnowledgeCategoryRepository;
import com.zyyqq.desk.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 知识库服务：文章与分类的增删改查、热门与检索、收藏与排序、缓存一致性 */
@Service
public class KnowledgeService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final RedisSupport redisSupport;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public KnowledgeService(KnowledgeArticleRepository articleRepository, KnowledgeCategoryRepository categoryRepository,
                            RedisSupport redisSupport, AppProperties props, JdbcTemplate jdbc) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.redisSupport = redisSupport;
        this.props = props;
        this.jdbc = jdbc;
    }

    /**
     * 文章查询公共片段：联表取分类名、作者名、收藏量、当前用户是否已收藏。
     * 收藏量与收藏状态是联表结果，Spring Data JDBC 会丢弃 @Transient 字段，因此统一用 Map 返回（首个参数为当前用户 ID）
     */
    private static final String ARTICLE_SELECT = """
            SELECT a.id, a.title, a.summary, a.category_id AS categoryId, a.keywords, a.status,
                   a.author_id AS authorId, a.view_count AS viewCount,
                   a.published_at AS publishedAt, a.created_at AS createdAt, a.updated_at AS updatedAt,
                   c.name AS categoryName, u.display_name AS authorName,
                   COALESCE(f.cnt, 0) AS favoriteCount,
                   CASE WHEN mf.id IS NULL THEN 0 ELSE 1 END AS favorited
            FROM knowledge_article a
            LEFT JOIN knowledge_category c ON c.id = a.category_id
            LEFT JOIN sys_user u ON u.id = a.author_id
            LEFT JOIN (SELECT article_id, COUNT(*) AS cnt FROM knowledge_favorite GROUP BY article_id) f
                   ON f.article_id = a.id
            LEFT JOIN knowledge_favorite mf ON mf.article_id = a.id AND mf.user_id = ?
            """;

    /** 查询知识分类列表（带缓存） */
    public List<KnowledgeCategory> categories() {
        return redisSupport.cacheJson("knowledge_categories", List.class,
                Duration.ofSeconds(props.getRedis().getCategoriesTtl()),
                categoryRepository::findAllOrdered);
    }

    /** 分页查询文章：支持关键字、排序方式（最新/最早/收藏最多/浏览最多）与只看我的收藏 */
    public PageResult<Map<String, Object>> list(int page, int pageSize, String keyword, String sort, boolean onlyFavorite) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int p = Math.max(page, 1);
        AuthContext.UserInfo u = AuthContext.require();

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        // 客户只看已发布文章，内部角色（客服/主管/管理员）可见草稿与待审核
        if ("CUSTOMER".equals(u.role())) {
            where.append(" AND a.status = 'PUBLISHED'");
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (a.title LIKE ? OR a.summary LIKE ? OR a.body LIKE ? OR a.keywords LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (onlyFavorite) {
            where.append(" AND mf.id IS NOT NULL");
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_article a "
                        + "LEFT JOIN knowledge_favorite mf ON mf.article_id = a.id AND mf.user_id = ?" + where,
                Long.class, concatArgs(u.id(), args));

        List<Map<String, Object>> rows = jdbc.queryForList(
                ARTICLE_SELECT + where + orderBy(sort) + " LIMIT ? OFFSET ?",
                concatArgs(u.id(), appendEnd(args, size, (p - 1) * size)));
        return PageResult.of(rows, total == null ? 0 : total, p, size);
    }

    /** 排序方式 → SQL（白名单，避免拼接注入；收藏量并列时按浏览量兜底） */
    private String orderBy(String sort) {
        return switch (sort == null ? "" : sort) {
            case "oldest" -> " ORDER BY COALESCE(a.published_at, a.created_at) ASC, a.id ASC";
            case "favorites" -> " ORDER BY favoriteCount DESC, a.view_count DESC, a.id DESC";
            case "views" -> " ORDER BY a.view_count DESC, favoriteCount DESC, a.id DESC";
            default -> " ORDER BY COALESCE(a.published_at, a.created_at) DESC, a.id DESC";
        };
    }

    /** 把首个用户 ID 与其余参数合并成一个参数数组 */
    private Object[] concatArgs(Long uid, List<Object> args) {
        Object[] all = new Object[args.size() + 1];
        all[0] = uid;
        for (int i = 0; i < args.size(); i++) {
            all[i + 1] = args.get(i);
        }
        return all;
    }

    /** 在参数末尾追加分页参数（不改动原列表） */
    private List<Object> appendEnd(List<Object> args, int limit, int offset) {
        List<Object> copy = new ArrayList<>(args);
        copy.add(limit);
        copy.add(offset);
        return copy;
    }

    /** 文章详情（客户仅能看已发布，浏览数自增；返回收藏量与本人收藏状态） */
    public Map<String, Object> detail(Long id) {
        Map<String, Object> a = findMap(id);
        AuthContext.UserInfo u = AuthContext.require();
        if (!"PUBLISHED".equals(a.get("status")) && "CUSTOMER".equals(u.role())) {
            throw BizException.forbidden("文章未发布");
        }
        articleRepository.incrView(id);
        // 自增后再返回，保证展示的浏览数与库内一致
        a.put("viewCount", toLong(a.get("viewCount")) + 1);
        return a;
    }

    /** 读取单篇文章（含分类/作者/收藏量），不存在则抛 404 */
    private Map<String, Object> findMap(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(ARTICLE_SELECT + " WHERE a.id = ?",
                AuthContext.require().id(), id);
        if (rows.isEmpty()) {
            throw BizException.notFound("文章不存在");
        }
        return rows.get(0);
    }

    /** 数字字段安全转换 */
    private long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
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

    /** 收藏文章（所有身份均可收藏任意文章，重复收藏幂等） */
    public Map<String, Object> favorite(Long id) {
        Map<String, Object> a = findMap(id);
        // 未发布文章仅内部角色可收藏
        if (!"PUBLISHED".equals(a.get("status")) && "CUSTOMER".equals(AuthContext.require().role())) {
            throw BizException.forbidden("文章未发布");
        }
        jdbc.update("INSERT IGNORE INTO knowledge_favorite (article_id, user_id) VALUES (?, ?)",
                id, AuthContext.require().id());
        return favoriteState(id);
    }

    /** 取消收藏 */
    public Map<String, Object> unfavorite(Long id) {
        jdbc.update("DELETE FROM knowledge_favorite WHERE article_id = ? AND user_id = ?",
                id, AuthContext.require().id());
        return favoriteState(id);
    }

    /** 返回指定文章的收藏总量与当前用户收藏状态 */
    private Map<String, Object> favoriteState(Long id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_favorite WHERE article_id = ?", Long.class, id);
        Long mine = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_favorite WHERE article_id = ? AND user_id = ?",
                Long.class, id, AuthContext.require().id());
        Map<String, Object> m = new HashMap<>();
        m.put("articleId", id);
        m.put("favoriteCount", count == null ? 0 : count);
        m.put("favorited", mine != null && mine > 0);
        return m;
    }

    /** 知识库数据概览：总量与 Top 文章（供主管/管理员的数据统计页使用） */
    public Map<String, Object> overview(int top) {
        int limit = Math.min(Math.max(top, 1), 50);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_article", Long.class);
        Long totalViews = jdbc.queryForObject("SELECT COALESCE(SUM(view_count), 0) FROM knowledge_article", Long.class);
        Long totalFavorites = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_favorite", Long.class);
        Long favUsers = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM knowledge_favorite", Long.class);

        Map<String, Object> m = new HashMap<>();
        m.put("total", total == null ? 0 : total);
        m.put("totalViews", totalViews == null ? 0 : totalViews);
        m.put("totalFavorites", totalFavorites == null ? 0 : totalFavorites);
        m.put("favoriteUsers", favUsers == null ? 0 : favUsers);
        // 篇均收藏，保留一位小数
        double avg = (total == null || total == 0) ? 0 : (double) (totalFavorites == null ? 0 : totalFavorites) / total;
        m.put("avgFavorites", Math.round(avg * 10) / 10.0);
        m.put("top", jdbc.queryForList("""
                SELECT a.id, a.title, a.view_count AS viewCount,
                       COALESCE(f.cnt, 0) AS favoriteCount,
                       a.published_at AS publishedAt, a.created_at AS createdAt,
                       c.name AS categoryName
                FROM knowledge_article a
                LEFT JOIN knowledge_category c ON c.id = a.category_id
                LEFT JOIN (SELECT article_id, COUNT(*) AS cnt FROM knowledge_favorite GROUP BY article_id) f
                       ON f.article_id = a.id
                ORDER BY favoriteCount DESC, a.view_count DESC
                LIMIT ?
                """, limit));
        return m;
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

    /** 删除文章与其收藏记录，并失效缓存 */
    public void delete(Long id) {
        jdbc.update("DELETE FROM knowledge_favorite WHERE article_id = ?", id);
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
