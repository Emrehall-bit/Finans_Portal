package com.emrehalli.financeportal.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class AiResponseCacheService {

    private static final Logger logger = LogManager.getLogger(AiResponseCacheService.class);
    private static final String KEY_PATTERN = "ai:*";

    private final RedisTemplate<String, String> redisStringTemplate;
    private final ObjectMapper objectMapper;
    // Per-key locks prevent duplicate LLM calls on concurrent cache misses
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public AiResponseCacheService(@Qualifier("redisStringTemplate") RedisTemplate<String, String> redisStringTemplate,
                                   ObjectMapper objectMapper) {
        this.redisStringTemplate = redisStringTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrCompute(String key, Class<T> type, Duration ttl, Supplier<T> supplier) {
        return getOrComputeWithDynamicTtl(key, type, () -> new CachedValue<>(supplier.get(), ttl));
    }

    public <T> T getOrComputeWithDynamicTtl(String key, Class<T> type, Supplier<CachedValue<T>> supplier) {
        T cached = fromRedis(key, type);
        if (cached != null) {
            logger.info("AI cache hit. key={}", key);
            return cached;
        }

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                T latest = fromRedis(key, type);
                if (latest != null) {
                    logger.info("AI cache hit. key={}", key);
                    return latest;
                }

                logger.info("AI cache miss, computing. key={}", key);
                CachedValue<T> computed = supplier.get();
                if (computed != null && computed.value() != null) {
                    toRedis(key, computed.value(), computed.ttl());
                    logger.info("AI cache stored. key={}, ttlMinutes={}", key, computed.ttl().toMinutes());
                }
                return computed == null ? null : computed.value();
            }
        } finally {
            locks.remove(key, lock);
        }
    }

    public void evict(String key) {
        Boolean deleted = redisStringTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            logger.info("AI cache evicted. key={}", key);
        } else {
            logger.info("AI cache evict miss, key not present. key={}", key);
        }
    }

    public int evictAll() {
        Set<String> keys = redisStringTemplate.keys(KEY_PATTERN);
        if (keys == null || keys.isEmpty()) {
            logger.info("AI cache cleared. evictedCount=0");
            return 0;
        }
        Long count = redisStringTemplate.delete(keys);
        int deleted = count != null ? count.intValue() : 0;
        logger.info("AI cache cleared. evictedCount={}", deleted);
        return deleted;
    }

    private <T> T fromRedis(String key, Class<T> type) {
        try {
            String json = redisStringTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            logger.warn("AI cache Redis read failed, treating as miss. key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void toRedis(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisStringTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            logger.warn("AI cache Redis write failed. key={}, reason={}", key, e.getMessage());
        }
    }

    public record CachedValue<T>(T value, Duration ttl) {}
}
