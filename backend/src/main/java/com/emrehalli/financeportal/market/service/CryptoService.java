package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
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
import com.emrehalli.financeportal.market.provider.crypto.dto.CryptoTickerDto;
import com.emrehalli.financeportal.market.support.BinancePairMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for crypto persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CryptoService {

    private static final String CACHE_KEY_ALL = "crypto:all";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketPriceHistoryRepository historyRepository;
    private final CacheService cacheService;
    private final BinancePairMapper binancePairMapper;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<CryptoTickerDto> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return;
        }

        for (CryptoTickerDto ticker : tickers) {
            try {
                if (ticker == null || ticker.getSymbol() == null || ticker.getSymbol().isBlank() || ticker.getPrice() == null) {
                    continue;
                }

                Instant todayStart = LocalDate.now(ZoneOffset.UTC)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

                String displayCode = normalizeCode(binancePairMapper.toDisplayCode(ticker.getSymbol()));
                if (displayCode.isBlank()) {
                    continue;
                }

                LocalDateTime timestamp = ticker.getDataTimestamp() != null ? ticker.getDataTimestamp() : LocalDateTime.now();
                MarketInstrument instrument = findOrCreateInstrument(displayCode);

                Optional<MarketPriceHistory> prevClose =
                        historyRepository.findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                                instrument,
                                IntervalType.ONE_DAY,
                                SourceName.BINANCE,
                                todayStart
                        );

                BigDecimal changeRate = prevClose.map(prev -> {
                    if (prev.getClosePrice() == null || prev.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
                        return null;
                    }
                    return ticker.getPrice()
                            .subtract(prev.getClosePrice())
                            .divide(prev.getClosePrice(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                }).orElse(null);

                MarketPrice savedPrice = marketPriceRepository.save(MarketPrice.builder()
                        .instrument(instrument)
                        .priceValue(ticker.getPrice())
                        .changeRate(changeRate)
                        .priceTimestamp(timestamp)
                        .sourceName(SourceName.BINANCE)
                        .build());

                putCacheSilently(buildCacheKey(displayCode), toDto(savedPrice));
            } catch (Exception exception) {
                log.warn("Failed to save crypto ticker for symbol={}", ticker != null ? ticker.getSymbol() : null, exception);
            }
        }

        evictCacheSilently(CACHE_KEY_ALL);
    }

    @Transactional(readOnly = true)
    public List<MarketQueryService.MarketSnapshot> getAll() {
        List<MarketQueryService.MarketSnapshot> cached = cacheService.getList(CACHE_KEY_ALL, MarketQueryService.MarketSnapshot.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<MarketQueryService.MarketSnapshot> snapshots = marketPriceRepository.findAll().stream()
                    .filter(price -> price.getSourceName() == SourceName.BINANCE)
                    .filter(price -> price.getInstrument() != null)
                    .filter(price -> price.getInstrument().getInstrumentType() == InstrumentType.CRYPTO)
                    .collect(Collectors.toMap(
                            price -> price.getInstrument().getId(),
                            Function.identity(),
                            (left, right) -> right.getPriceTimestamp().isAfter(left.getPriceTimestamp()) ? right : left
                    ))
                    .values().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .filter(this::hasNonBlankSymbol)
                    .sorted(Comparator.comparing(MarketQueryService.MarketSnapshot::symbol))
                    .toList();
            putCacheSilently(CACHE_KEY_ALL, snapshots);
            return snapshots;
        } catch (Exception exception) {
            log.error("Failed to load all crypto data from database. Returning cached values when available.", exception);
            return cacheService.getList(CACHE_KEY_ALL, MarketQueryService.MarketSnapshot.class);
        }
    }

    @Transactional(readOnly = true)
    public MarketQueryService.MarketSnapshot getBySymbol(String symbol) {
        String normalizedSymbol = normalizeCode(symbol);
        MarketQueryService.MarketSnapshot cached = getCachedSnapshot(normalizedSymbol);
        if (cached != null) {
            return cached;
        }

        try {
            MarketQueryService.MarketSnapshot snapshot = marketInstrumentRepository
                    .findByInstrumentCodeIgnoreCaseAndSourceName(normalizedSymbol, SourceName.BINANCE)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.CRYPTO)
                    .flatMap(marketPriceRepository::findTopByInstrumentOrderByPriceTimestampDesc)
                    .map(this::toDto)
                    .orElse(null);

            if (snapshot != null) {
                putCacheSilently(buildCacheKey(normalizedSymbol), snapshot);
            }
            return snapshot;
        } catch (Exception exception) {
            log.error("Failed to load crypto data for symbol {}. Returning cached value when available.", normalizedSymbol, exception);
            return getCachedSnapshot(normalizedSymbol);
        }
    }

    private MarketInstrument findOrCreateInstrument(String symbol) {
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCaseAndSourceName(symbol, SourceName.BINANCE)
                .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(symbol)
                        .instrumentName(symbol + "/TRY")
                        .instrumentType(InstrumentType.CRYPTO)
                        .sourceName(SourceName.BINANCE)
                        .build()));
    }

    private MarketQueryService.MarketSnapshot toDto(MarketPrice price) {
        if (price == null || price.getInstrument() == null) {
            return null;
        }

        return new MarketQueryService.MarketSnapshot(
                price.getInstrument().getInstrumentCode(),
                price.getInstrument().getInstrumentName(),
                price.getPriceValue(),
                price.getChangeRate(),
                price.getSourceName().name(),
                price.getInstrument().getInstrumentType().name(),
                "TRY",
                price.getPriceTimestamp()
        );
    }

    private MarketQueryService.MarketSnapshot getCachedSnapshot(String symbol) {
        try {
            return cacheService.get(buildCacheKey(symbol), MarketQueryService.MarketSnapshot.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read crypto cache for symbol={}", symbol, exception);
        }
        return null;
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getCrypto());
        } catch (Exception exception) {
            log.warn("Failed to write crypto cache for key={}", key, exception);
        }
    }

    private void evictCacheSilently(String key) {
        try {
            cacheService.evict(key);
        } catch (Exception exception) {
            log.warn("Failed to evict crypto cache for key={}", key, exception);
        }
    }

    private String buildCacheKey(String symbol) {
        return "crypto:" + symbol + ":TRY";
    }

    private String normalizeCode(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private boolean hasNonBlankSymbol(MarketQueryService.MarketSnapshot snapshot) {
        return snapshot != null && snapshot.symbol() != null && !snapshot.symbol().isBlank();
    }
}



