package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class MarketDataCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CURRENT_MARKET_DATA_KEY = "MARKET_DATA:CURRENT:ALL";
    private static final String PROVIDER_KEY_PREFIX = "MARKET_DATA:CURRENT:PROVIDER:";

    public MarketDataCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateCurrentData(List<MarketDataDto> data) {
        List<MarketDataDto> safeData = enrich(data);
        redisTemplate.opsForValue().set(CURRENT_MARKET_DATA_KEY, safeData, Duration.ofMinutes(15));
    }

    public void updateProviderData(String providerName, List<MarketDataDto> data) {
        if (providerName == null || providerName.isBlank()) {
            return;
        }

        redisTemplate.opsForValue().set(buildProviderKey(providerName), enrich(data), Duration.ofMinutes(15));
    }

    public List<MarketDataDto> getCurrentData() {
        return readList(CURRENT_MARKET_DATA_KEY);
    }

    public List<MarketDataDto> getCurrentDataByProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return List.of();
        }

        return readList(buildProviderKey(providerName));
    }

    public List<MarketDataDto> getCurrentDataByType(InstrumentType instrumentType) {
        if (instrumentType == null) {
            return List.of();
        }

        return getCurrentData().stream()
                .filter(item -> instrumentType == item.getInstrumentType())
                .toList();
    }

    public Optional<MarketDataDto> findCurrentBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        String normalizedSymbol = normalizeSymbol(symbol);

        return getCurrentData().stream()
                .filter(item -> item.getSymbol() != null)
                .filter(item -> normalizeSymbol(item.getSymbol()).equals(normalizedSymbol))
                .max(Comparator.comparing(MarketDataDto::getFetchedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private String normalizeSymbol(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String buildProviderKey(String providerName) {
        return PROVIDER_KEY_PREFIX + providerName.toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<MarketDataDto> readList(String key) {
        Object value = redisTemplate.opsForValue().get(key);

        if (value instanceof List<?> list) {
            return enrich((List<MarketDataDto>) list);
        }

        return List.of();
    }

    private List<MarketDataDto> enrich(List<MarketDataDto> data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }

        List<MarketDataDto> result = new ArrayList<>();
        for (MarketDataDto item : data) {
            if (item == null) {
                continue;
            }

            item.setFreshness(MarketDataFreshness.from(item.getPriceTime(), item.getFetchedAt()));
            result.add(item);
        }

        return result.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Deprecated(forRemoval = false)
    public void updateTcmbData(List<MarketDataDto> data) {
        updateProviderData("EVDS", data);
        updateCurrentData(data);
    }

    @Deprecated(forRemoval = false)
    public List<MarketDataDto> getTcmbData() {
        return getCurrentDataByProvider("EVDS");
    }

    @Deprecated(forRemoval = false)
    public MarketDataDto findBySymbol(String symbol) {
        return findCurrentBySymbol(symbol).orElse(null);
    }

    public boolean isEmpty() {
        return getCurrentData().isEmpty();
    }
}
