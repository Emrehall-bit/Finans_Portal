package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.exception.CacheUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed cache service for market data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisCacheService implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void put(String key, Object value, long ttlMinutes) {
        if (value == null) {
            log.warn("Skipping cache write for key={} because value is null", key);
            return;
        }
        if (isEmptyValue(value)) {
            log.warn("Skipping cache write for key={} because value is empty", key);
            return;
        }

        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttlMinutes));
        } catch (Exception exception) {
            log.error("Failed to put market cache entry for key={}", key, exception);
            throw new CacheUnavailableException("Failed to put cache entry for key: " + key, exception);
        }
    }

    @Override
    public Optional<Object> get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (isEmptyValue(value)) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (Exception exception) {
            log.error("Failed to get market cache entry for key={}", key, exception);
            throw new CacheUnavailableException("Failed to get cache entry for key: " + key, exception);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).map(value -> objectMapper.convertValue(value, type));
    }

    @Override
    public <T> List<T> getList(String key, Class<T> elementType) {
        Optional<Object> cached = get(key);
        if (cached.isEmpty()) {
            return List.of();
        }

        Object value = cached.get();
        if (value instanceof Collection<?> collection) {
            List<T> results = new ArrayList<>(collection.size());
            for (Object item : collection) {
                results.add(objectMapper.convertValue(item, elementType));
            }
            return results;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<T> results = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                results.add(objectMapper.convertValue(Array.get(value, index), elementType));
            }
            return results;
        }

        return List.of();
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception exception) {
            log.error("Failed to evict market cache entry for key={}", key, exception);
            throw new CacheUnavailableException("Failed to evict cache entry for key: " + key, exception);
        }
    }

    @Override
    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                log.info("Market cache pattern evict pattern={} deleted=0", pattern);
                return;
            }
            redisTemplate.delete(keys);
            log.info("Market cache pattern evict pattern={} deleted={}", pattern, keys.size());
        } catch (Exception exception) {
            log.error("Failed to evict market cache entries for pattern={}", pattern, exception);
            throw new CacheUnavailableException("Failed to evict cache entries for pattern: " + pattern, exception);
        }
    }

    private boolean isEmptyValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0;
        }
        return false;
    }

    private int itemCount(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }
}
