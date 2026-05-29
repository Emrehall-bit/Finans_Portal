package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private static final String INTERNAL_CASH_CODE = "TRY";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
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
            if (rate.getSourceName() == SourceName.INTERNAL) {
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
        List<FxRateResponse> cached = readCachedListSafely(CACHE_KEY_ALL, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return filterBrowsableResponses(cached);
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments();
            List<FxRateResponse> responses = resolveResponses(instruments, null, null);
            putCacheSilently(CACHE_KEY_ALL, responses);
            return filterBrowsableResponses(responses);
        } catch (Exception exception) {
            log.error("Failed to load all FX rates from database. Returning cached values when available.", exception);
            return filterBrowsableResponses(readCachedListSafely(CACHE_KEY_ALL, FxRateResponse.class));
        }
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getBySource(SourceName sourceName) {
        if (sourceName == null) {
            return getAll();
        }

        String cacheKey = buildSourceCacheKey(sourceName);
        List<FxRateResponse> cached = readCachedListSafely(cacheKey, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return filterBrowsableResponses(cached);
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments().stream()
                    .filter(instrument -> instrument.getSourceName() == sourceName)
                    .toList();
            List<FxRateResponse> responses = resolveResponses(instruments, sourceName, null);
            putCacheSilently(cacheKey, responses);
            return filterBrowsableResponses(responses);
        } catch (Exception exception) {
            log.error("Failed to load FX rates for source {}. Returning cached values when available.", sourceName, exception);
            return filterBrowsableResponses(readCachedListSafely(cacheKey, FxRateResponse.class));
        }
    }

    @Transactional(readOnly = true)
    public List<FxRateResponse> getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        String cacheKey = buildCodeCacheKey(normalizedCode);
        List<FxRateResponse> cached = readCachedListSafely(cacheKey, FxRateResponse.class);
        if (!cached.isEmpty()) {
            return filterBrowsableResponses(cached);
        }

        try {
            List<MarketInstrument> instruments = getFxInstruments().stream()
                    .filter(instrument -> parseInstrumentKey(instrument).currencyCode().equals(normalizedCode))
                    .toList();
            List<FxRateResponse> responses = resolveResponses(instruments, null, normalizedCode);
            putCacheSilently(cacheKey, responses);
            return filterBrowsableResponses(responses);
        } catch (Exception exception) {
            log.error("Failed to load FX rates for code {}. Returning cached values when available.", normalizedCode, exception);
            return filterBrowsableResponses(readCachedListSafely(cacheKey, FxRateResponse.class));
        }
    }

    @Transactional(readOnly = true)
    public Optional<FxRateResponse> getBySourceAndCode(SourceName sourceName, String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return Optional.empty();
        }

        if (sourceName == null) {
            return getByCode(normalizedCode).stream().findFirst();
        }

        return getBySource(sourceName).stream()
                .filter(rate -> normalizedCode.equalsIgnoreCase(rate.getCode()))
                .findFirst();
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
        BigDecimal changeRate = calculatePersistedChangeRate(instrument, priceValue);
        log.debug("[FxService] savePrice changeRate={}, instrument={}",
                changeRate, instrument.getInstrumentCode());
        MarketPrice marketPrice = MarketPrice.builder()
                .instrument(instrument)
                .priceValue(priceValue)
                .changeRate(changeRate)
                .priceTimestamp(timestamp)
                .sourceName(sourceName)
                .build();
        marketPriceRepository.save(marketPrice);
    }

    private MarketInstrument findOrCreateInstrument(SourceName sourceName, String currencyCode, FxPriceType priceType) {
        String instrumentCode = buildInstrumentCode(sourceName, currencyCode, priceType);
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCaseAndSourceName(instrumentCode, sourceName)
                .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(instrumentCode)
                        .instrumentName(currencyCode + " " + priceType.name())
                        .instrumentType(InstrumentType.FX)
                        .sourceName(sourceName)
                        .build()));
    }

    private List<MarketInstrument> getFxInstruments() {
        return marketInstrumentRepository.findAll().stream()
                .filter(this::isBrowsableFxInstrument)
                .toList();
    }

    private boolean isBrowsableFxInstrument(MarketInstrument instrument) {
        return instrument.getInstrumentType() == InstrumentType.FX
                && instrument.getSourceName() != SourceName.INTERNAL;
    }

    private List<FxRateResponse> filterBrowsableResponses(List<FxRateResponse> responses) {
        return responses.stream()
                .filter(Objects::nonNull)
                .filter(response -> !SourceName.INTERNAL.name().equalsIgnoreCase(response.getSource()))
                .filter(response -> !INTERNAL_CASH_CODE.equalsIgnoreCase(response.getCode()))
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
        Map<FxPriceType, MarketInstrument> instrumentsByType = new EnumMap<>(FxPriceType.class);

        for (MarketInstrument instrument : instruments) {
            InstrumentKey instrumentKey = parseInstrumentKey(instrument);
            if (instrumentKey.sourceName() != sourceName || !instrumentKey.currencyCode().equals(currencyCode)) {
                continue;
            }

            instrumentsByType.put(instrumentKey.priceType(), instrument);
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

        log.debug("[FxService] buildFromDatabase changePercent hesaplaniyor. sourceName={}, currencyCode={}",
                sourceName, currencyCode);
        BigDecimal changePercent = sourceName == SourceName.INTERNAL
                ? BigDecimal.ZERO
                : calculateChangePercent(
                        sourceName,
                        latestPrices.get(FxPriceType.SELL),
                        instrumentsByType.get(FxPriceType.SELL)
                );
        log.debug("[FxService] buildFromDatabase changePercent={}", changePercent);
        log.debug("[FxService] FxRateResponse changePercent set ediliyor: {}", changePercent);

        return FxRateResponse.builder()
                .code(currencyCode)
                .name(currencyCode)
                .source(sourceName.name())
                .type(InstrumentType.FX.name())
                .buyRate(getPriceValue(latestPrices, FxPriceType.BUY))
                .sellRate(getPriceValue(latestPrices, FxPriceType.SELL))
                .last(resolveLast(latestPrices))
                .changePercent(changePercent)
                .priceTimestamp(priceTimestamp)
                .build();
    }

    private BigDecimal calculateChangePercent(SourceName sourceName, MarketPrice currentSellPrice, MarketInstrument sellInstrument) {
        if (currentSellPrice == null || currentSellPrice.getPriceValue() == null || currentSellPrice.getPriceTimestamp() == null || sellInstrument == null) {
            return null;
        }

        Instant todayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        Optional<MarketPriceHistory> previousClose = marketPriceHistoryRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                        sellInstrument,
                        IntervalType.ONE_DAY,
                        sourceName,
                        todayStart
                );

        BigDecimal previousClosePrice = previousClose
                .map(MarketPriceHistory::getClosePrice)
                .orElse(null);

        if (previousClosePrice == null || previousClosePrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return currentSellPrice.getPriceValue()
                .subtract(previousClosePrice)
                .divide(previousClosePrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
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
            FxRateResponse cached = cacheService.get(buildCacheKey(sourceName, currencyCode), FxRateResponse.class).orElse(null);
            if (cached == null) {
                return null;
            }
            if (sourceName == SourceName.TCMB
                    && cached.getSellRate() != null
                    && (cached.getBuyRate() == null || cached.getChangePercent() == null)) {
                return null;
            }
            return cached;
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
        BigDecimal last = firstNonNull(sellRate, existing != null ? existing.getLast() : null, rate.getReferencePrice());
        LocalDateTime priceTimestamp = existing == null
                ? timestamp
                : latestTimestamp(existing.getPriceTimestamp(), timestamp);
        BigDecimal changePercent = calculateChangePercent(sourceName, currencyCode, sellRate, priceTimestamp);

        mergedRates.put(cacheKey, FxRateResponse.builder()
                .code(currencyCode)
                .name(currencyCode)
                .source(sourceName.name())
                .type(InstrumentType.FX.name())
                .buyRate(buyRate)
                .sellRate(sellRate)
                .last(last)
                .changePercent(changePercent)
                .priceTimestamp(priceTimestamp)
                .build());
    }

    private BigDecimal calculateChangePercent(SourceName sourceName, String currencyCode, BigDecimal currentSellPrice, LocalDateTime currentTimestamp) {
        if (currentSellPrice == null || currentTimestamp == null) {
            return null;
        }

        return marketInstrumentRepository.findByInstrumentCodeIgnoreCaseAndSourceName(
                        buildInstrumentCode(sourceName, currencyCode, FxPriceType.SELL),
                        sourceName
                )
                .map(sellInstrument -> MarketPrice.builder()
                        .instrument(sellInstrument)
                        .sourceName(sourceName)
                        .priceValue(currentSellPrice)
                        .priceTimestamp(currentTimestamp)
                        .build())
                .map(currentSell -> calculateChangePercent(sourceName, currentSell, currentSell.getInstrument()))
                .orElse(null);
    }

    private BigDecimal calculatePersistedChangeRate(MarketInstrument instrument, BigDecimal currentPrice) {
        if (instrument == null || currentPrice == null) {
            log.debug("[FxService] calculatePersistedChangeRate: instrument veya currentPrice null, atlaniyor.");
            return null;
        }

        Instant todayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        log.debug("[FxService] changeRate hesaplaniyor. instrument={}, todayStart={}, currentPrice={}",
                instrument.getInstrumentCode(), todayStart, currentPrice);

        Optional<MarketPriceHistory> prevClose = marketPriceHistoryRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY,
                        SourceName.TCMB,
                        todayStart
                );

        log.debug("[FxService] prevClose bulundu mu: {}", prevClose.isPresent());
        if (prevClose.isPresent()) {
            log.debug("[FxService] prevClose.closePrice={}, priceTimestamp={}",
                    prevClose.get().getClosePrice(),
                    prevClose.get().getPriceTimestamp());
        }

        return prevClose.map(prev -> {
            if (prev.getClosePrice() == null || prev.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return currentPrice
                    .subtract(prev.getClosePrice())
                    .divide(prev.getClosePrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }).orElse(null);
    }

    private void putCacheSilently(String key, Object response) {
        try {
            cacheService.put(key, response, props.getCache().getTtlMinutes().getFx());
        } catch (Exception exception) {
            log.warn("Failed to write FX cache for key={}", key, exception);
        }
    }

    private <T> List<T> readCachedListSafely(String key, Class<T> elementType) {
        try {
            return cacheService.getList(key, elementType);
        } catch (Exception exception) {
            log.warn("Failed to read FX cache list for key={}", key, exception);
            return List.of();
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



