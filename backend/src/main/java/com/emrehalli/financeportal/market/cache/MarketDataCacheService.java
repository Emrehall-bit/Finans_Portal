package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class MarketDataCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY = "TCMB_MARKET_DATA";

    public MarketDataCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateTcmbData(List<MarketDataDto> data) {
        redisTemplate.opsForValue().set(KEY, data, Duration.ofMinutes(15));
    }

    public List<MarketDataDto> getTcmbData() {
        return (List<MarketDataDto>) redisTemplate.opsForValue().get(KEY);
    }
    public MarketDataDto findBySymbol(String symbol) {
        List<MarketDataDto> data = getTcmbData();

        if (data == null || data.isEmpty() || symbol == null || symbol.isBlank()) {
            return null;
        }

        String normalizedSymbol = normalizeSymbol(symbol);

        return data.stream()
                .filter(item -> item.getSymbol() != null)
                .filter(item -> normalizeSymbol(item.getSymbol()).equals(normalizedSymbol))
                .findFirst()
                .orElse(null);
    }

    private String normalizeSymbol(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
    public boolean isEmpty() {
        return getTcmbData() == null;
    }
}