package com.emrehalli.financeportal.market.cache;

import java.util.List;
import java.util.Optional;

/**
 * Cache service contract for market data.
 */
public interface CacheService {

    void put(String key, Object value, long ttlMinutes);

    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    <T> List<T> getList(String key, Class<T> elementType);

    void evict(String key);

    void evictByPattern(String pattern);
}
