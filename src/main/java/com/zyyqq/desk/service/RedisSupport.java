package com.zyyqq.desk.service;

import com.zyyqq.desk.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Redis 统一支撑层。
 *
 * Key 命名规则：
 *   {prefix}:cache:*     热点数据缓存（热门知识/分类/统计）
 *   {prefix}:auth:*      登录安全（失败计数锁定）
 *   {prefix}:ratelimit:* 接口访问限流
 *   {prefix}:idem:*      工单重复提交幂等标识
 *
 * 降级策略：
 *   - 缓存类操作：Redis 异常时直接回源 MySQL（fail-open），核心业务不受影响；
 *   - 限流/登录安全：Redis 异常时放行并记录 WARN 日志，保证可用性（安全控制依赖 Redis 正常时的计数）；
 *   - 幂等：Redis 异常时跳过幂等检查，由数据库唯一约束兜底。
 */
@Slf4j
@Service
public class RedisSupport {

    private final StringRedisTemplate redis;
    private final AppProperties props;
    private final boolean available;
    private final tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v", Long.class);

    public RedisSupport(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.props = props;
        boolean ok = false;
        try {
            Object pong = redis.getConnectionFactory().getConnection().ping();
            ok = "PONG".equalsIgnoreCase(String.valueOf(pong));
        } catch (Exception e) {
            log.warn("Redis 初始连接异常：{}", e.getMessage());
        }
        this.available = ok;
        if (ok) {
            log.info("Redis 连接正常，缓存/限流/幂等功能已启用");
        } else {
            log.warn("Redis 不可用，系统以降级模式运行（直接回源数据库，限流/幂等部分功能减弱）");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private String key(String scene, String name) {
        return props.getRedis().getKeyPrefix() + ":" + scene + ":" + name;
    }

    /** 读取 JSON 缓存，未命中或异常返回 null 并回源 supplier */
    public <T> T cacheJson(String name, Class<T> type, Duration ttl, Supplier<T> loader) {
        String k = key("cache", name);
        if (available) {
            try {
                String cached = redis.opsForValue().get(k);
                if (cached != null) {
                    log.debug("缓存命中: {}", k);
                    return mapper.readValue(cached, type);
                }
                log.debug("缓存未命中: {}", k);
            } catch (Exception e) {
                log.warn("Redis 读取异常，回源数据库: {} ({})", k, e.getMessage());
            }
        }
        T value = loader.get();
        if (available && value != null) {
            try {
                redis.opsForValue().set(k, mapper.writeValueAsString(value), ttl);
            } catch (Exception e) {
                log.warn("Redis 写入缓存失败: {} ({})", k, e.getMessage());
            }
        }
        return value;
    }

    /** 按前缀失效缓存，如 evictCache("hot_articles") */
    public void evictCache(String... names) {
        if (!available) return;
        try {
            for (String n : names) {
                redis.delete(key("cache", n));
            }
        } catch (Exception e) {
            log.warn("Redis 缓存失效操作失败: {}", e.getMessage());
        }
    }

    /**
     * 固定窗口限流计数，返回窗口内请求数。Redis 异常时返回 -1（调用方决定放行）。
     */
    public long rateLimitIncr(String scene, String name, int windowSeconds) {
        String k = key(scene, name);
        try {
            Long v = redis.execute(RATE_LIMIT_SCRIPT, List.of(k), String.valueOf(windowSeconds));
            return v == null ? -1 : v;
        } catch (Exception e) {
            log.warn("Redis 限流计数异常（降级放行）: {} ({})", k, e.getMessage());
            return -1;
        }
    }

    /** 登录失败计数 +1，返回累计失败次数。Redis 异常返回 -1（不锁定）。 */
    public long recordLoginFailure(String username, int windowSeconds) {
        try {
            return rateLimitIncr("auth", "fail:" + username, windowSeconds);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 登录成功后清除失败计数 */
    public void clearLoginFailures(String username) {
        try {
            redis.delete(key("auth", "fail:" + username));
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 幂等锁：同一请求标识在 TTL 内只允许提交一次。
     * @return true=首次提交（放行），false=重复提交（拒绝）；Redis 异常时返回 true（数据库约束兜底）
     */
    public boolean tryIdempotent(String requestKey, Duration ttl) {
        String k = key("idem", requestKey);
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(k, "1", ttl);
            return !Boolean.FALSE.equals(ok);
        } catch (Exception e) {
            log.warn("Redis 幂等检查异常（跳过幂等，由数据库约束兜底）: {} ({})", k, e.getMessage());
            return true;
        }
    }
}
