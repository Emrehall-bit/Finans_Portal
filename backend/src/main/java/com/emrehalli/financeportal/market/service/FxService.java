package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Service for FX persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FxService {

    private static final String CACHE_KEY_ALL = "fx:all";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CacheService cacheService;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<FxRateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }

        Map<String, FxRateResponse> mergedRates = new HashMap<>();

        for (FxRateDto rate : rates) {
            if (rate == null || rate.getSourceName() == null || rate.getCurrencyCode() == null || rate.getCurrencyCode().isBlank()) {
                continue;
            }

            String currencyCode = normalizeCode(rate.getCurrencyCode());
            LocalDateTime timestamp = rate.getDataTimestamp() != null ? rate.getDataTimestamp() : LocalDateTime.now();

            savePrice(rate.getSourceName(), currencyCode, FxPriceType.BUY, rate.getBuyPrice(), timestamp);
            savePrice(rate.getSourceName(), currencyCode, FxPriceType.SELL, rate.getSellPrice(), timestamp);
            savePrice(rate.getSourceName(), currencyCode, FxPriceType.REFERENCE, rate.getReferencePrice(), timestamp);
            mergeRate(mergedRates, rate.getSourceName(), currencyCode, rate, timestamp);
        }

        for (Map.Entry<String, FxRateResponse> entry : mergedRates.entrySet()) {
            putCacheSilently(entry.getKey(), entry.getValue());
        }

        evictAggregateCaches(rates);
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getAll() {
        List<FxRateResponse> cached = cacheService.getList(CACHE_KEY_ALL, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments();
            List<FxRateResponse> responses = resolveResponses(instruments, null, null);
            putCacheSilently(CACHE_KEY_ALL, responses);
            return responses;
        } catch (Exception exception) {
            log.error("Failed to load all FX rates from database. Returning cached values when available.", exception);
            return cacheService.getList(CACHE_KEY_ALL, FxRateResponse.class);
        }
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getBySource(SourceName sourceName) {
        if (sourceName == null) {
            return getAll();
        }

        String cacheKey = buildSourceCacheKey(sourceName);
        List<FxRateResponse> cached = cacheService.getList(cacheKey, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments().stream()
                    .filter(instrument -> instrument.getSourceName() == sourceName)
                    .toList();
            List<FxRateResponse> responses = resolveResponses(instruments, sourceName, null);
            putCacheSilently(cacheKey, responses);
            return responses;
        } catch (Exception exception) {
            log.error("Failed to load FX rates for source {}. Returning cached values when available.", sourceName, exception);
            return cacheService.getList(cacheKey, FxRateResponse.class);
        }
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        String cacheKey = buildCodeCacheKey(normalizedCode);
        List<FxRateResponse> cached = cacheService.getList(cacheKey, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments().stream()
                    .filter(instrument -> parseInstrumentKey(instrument).currencyCode().equals(normalizedCode))
                    .toList();
            List<FxRateResponse> responses = resolveResponses(instruments, null, normalizedCode);
            putCacheSilently(cacheKey, responses);
            return responses;
        } catch (Exception exception) {
            log.error("Failed to load FX rates for code {}. Returning cached values when available.", normalizedCode, exception);
            return cacheService.getList(cacheKey, FxRateResponse.class);
        }
    }

    private void savePrice(SourceName sourceName,
                           String currencyCode,
                           FxPriceType priceType,
                           BigDecimal priceValue,
                           LocalDateTime timestamp) {
        if (priceValue == null) {
            return;
        }

        MarketInstrument instrument = findOrCreateInstrument(sourceName, currencyCode, priceType);
        MarketPrice marketPrice = MarketPrice.builder()
                .instrument(instrument)
                .priceValue(priceValue)
                .priceTimestamp(timestamp)
                .sourceName(sourceName)
                .build();
        marketPriceRepository.save(marketPrice);
    }

    private MarketInstrument findOrCreateInstrument(SourceName sourceName, String currencyCode, FxPriceType priceType) {
        String instrumentCode = buildInstrumentCode(sourceName, currencyCode, priceType);
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(instrumentCode)
                        .instrumentName(currencyCode + " " + priceType.name())
                        .instrumentType(InstrumentType.FX)
                        .sourceName(sourceName)
                        .build()));
    }

    private List<MarketInstrument> getFxInstruments() {
        return marketInstrumentRepository.findAll().stream()
                .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FX)
                .toList();
    }

    private List<FxRateResponse> resolveResponses(List<MarketInstrument> instruments,
                                                  SourceName sourceFilter,
                                                  String codeFilter) {
        Set<String> pairKeys = new TreeSet<>();
        for (MarketInstrument instrument : instruments) {
            InstrumentKey instrumentKey = parseInstrumentKey(instrument);
            if (sourceFilter != null && instrumentKey.sourceName() != sourceFilter) {
                continue;
            }
            if (codeFilter != null && !instrumentKey.currencyCode().equals(codeFilter)) {
                continue;
            }
            pairKeys.add(instrumentKey.sourceName().name() + ":" + instrumentKey.currencyCode());
        }

        List<FxRateResponse> responses = new ArrayList<>();
        for (String pairKey : pairKeys) {
            String[] parts = pairKey.split(":");
            SourceName sourceName = SourceName.valueOf(parts[0]);
            String currencyCode = parts[1];

            FxRateResponse cached = getCachedRate(sourceName, currencyCode);
            if (cached != null) {
                responses.add(cached);
                continue;
            }

            FxRateResponse dbValue = buildFromDatabase(instruments, sourceName, currencyCode);
            if (dbValue != null) {
                putCacheSilently(buildCacheKey(sourceName, currencyCode), dbValue);
                responses.add(dbValue);
            }
        }

        responses.sort(Comparator.comparing(FxRateResponse::getSource).thenComparing(FxRateResponse::getCode));
        return responses;
    }

    private FxRateResponse buildFromDatabase(List<MarketInstrument> instruments, SourceName sourceName, String currencyCode) {
        Map<FxPriceType, MarketPrice> latestPrices = new EnumMap<>(FxPriceType.class);

        for (MarketInstrument instrument : instruments) {
            InstrumentKey instrumentKey = parseInstrumentKey(instrument);
            if (instrumentKey.sourceName() != sourceName || !instrumentKey.currencyCode().equals(currencyCode)) {
                continue;
            }

            marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument)
                    .ifPresent(price -> latestPrices.put(instrumentKey.priceType(), price));
        }

        if (latestPrices.isEmpty()) {
            return null;
        }

        LocalDateTime priceTimestamp = latestPrices.values().stream()
                .map(MarketPrice::getPriceTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return FxRateResponse.builder()
                .code(currencyCode)
                .name(currencyCode)
                .source(sourceName.name())
                .type(InstrumentType.FX.name())
                .buyRate(getPriceValue(latestPrices, FxPriceType.BUY))
                .sellRate(getPriceValue(latestPrices, FxPriceType.SELL))
                .last(resolveLast(latestPrices))
                .priceTimestamp(priceTimestamp)
                .build();
    }

    private BigDecimal resolveLast(Map<FxPriceType, MarketPrice> latestPrices) {
        BigDecimal sell = getPriceValue(latestPrices, FxPriceType.SELL);
        if (sell != null) {
            return sell;
        }
        return getPriceValue(latestPrices, FxPriceType.REFERENCE);
    }

    private BigDecimal getPriceValue(Map<FxPriceType, MarketPrice> latestPrices, FxPriceType priceType) {
        return Optional.ofNullable(latestPrices.get(priceType))
                .map(MarketPrice::getPriceValue)
                .orElse(null);
    }

    private FxRateResponse getCachedRate(SourceName sourceName, String currencyCode) {
        try {
            return cacheService.get(buildCacheKey(sourceName, currencyCode), FxRateResponse.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read FX cache for {} {}", sourceName, currencyCode, exception);
        }
        return null;
    }

    private void mergeRate(Map<String, FxRateResponse> mergedRates,
                           SourceName sourceName,
                           String currencyCode,
                           FxRateDto rate,
                           LocalDateTime timestamp) {
        String cacheKey = buildCacheKey(sourceName, currencyCode);
        FxRateResponse existing = mergedRates.get(cacheKey);

        BigDecimal buyRate = firstNonNull(rate.getBuyPrice(), existing != null ? existing.getBuyRate() : null);
        BigDecimal sellRate = firstNonNull(rate.getSellPrice(), existing != null ? existing.getSellRate() : null);
        BigDecimal last = firstNonNull(sellRate, rate.getReferencePrice(), existing != null ? existing.getLast() : null);
        LocalDateTime priceTimestamp = existing == null
                ? timestamp
                : latestTimestamp(existing.getPriceTimestamp(), timestamp);

        mergedRates.put(cacheKey, FxRateResponse.builder()
                .code(currencyCode)
                .name(currencyCode)
                .source(sourceName.name())
                .type(InstrumentType.FX.name())
                .buyRate(buyRate)
                .sellRate(sellRate)
                .last(last)
                .priceTimestamp(priceTimestamp)
                .build());
    }

    private void putCacheSilently(String key, Object response) {
        try {
            cacheService.put(key, response, props.getCache().getTtlMinutes().getFx());
        } catch (Exception exception) {
            log.warn("Failed to write FX cache for key={}", key, exception);
        }
    }

    private String buildInstrumentCode(SourceName sourceName, String currencyCode, FxPriceType priceType) {
        return sourceName.name() + ":" + currencyCode + ":" + priceType.name();
    }

    private String buildCacheKey(SourceName sourceName, String currencyCode) {
        return "fx:" + sourceName.name() + ":" + currencyCode;
    }

    private String buildSourceCacheKey(SourceName sourceName) {
        return "fx:source:" + sourceName.name();
    }

    private String buildCodeCacheKey(String currencyCode) {
        return "fx:code:" + currencyCode;
    }

    private void evictAggregateCaches(List<FxRateDto> rates) {
        evictCacheSilently(CACHE_KEY_ALL);
        for (FxRateDto rate : rates) {
            if (rate == null || rate.getSourceName() == null || rate.getCurrencyCode() == null) {
                continue;
            }
            String currencyCode = normalizeCode(rate.getCurrencyCode());
            evictCacheSilently(buildSourceCacheKey(rate.getSourceName()));
            evictCacheSilently(buildCodeCacheKey(currencyCode));
        }
    }

    public void evictFxCache() {
        evictCacheSilently("fx:*");
    }

    private void evictCacheSilently(String key) {
        try {
            if (key.contains("*")) {
                cacheService.evictByPattern(key);
                return;
            }
            cacheService.evict(key);
        } catch (Exception exception) {
            log.warn("Failed to evict FX cache for key={}", key, exception);
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime latestTimestamp(LocalDateTime current, LocalDateTime candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }

    private InstrumentKey parseInstrumentKey(MarketInstrument instrument) {
        String[] parts = instrument.getInstrumentCode().split(":");
        if (parts.length == 3) {
            return new InstrumentKey(
                    SourceName.valueOf(parts[0]),
                    parts[1],
                    FxPriceType.valueOf(parts[2])
            );
        }

        return new InstrumentKey(
                instrument.getSourceName(),
                normalizeCode(instrument.getInstrumentCode()),
                FxPriceType.REFERENCE
        );
    }

    private record InstrumentKey(SourceName sourceName, String currencyCode, FxPriceType priceType) {
    }

    private enum FxPriceType {
        BUY,
        SELL,
        REFERENCE
    }
}
