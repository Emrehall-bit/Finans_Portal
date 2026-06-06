package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.BistTier;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.StockSector;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.InstrumentNotFoundException;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for stock persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockService {

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;
    private final BistSymbolRegistry bistSymbolRegistry;
    private final MarketDailyChangeService dailyChangeService;

    @Transactional
    public void saveAll(List<StockPriceDto> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        for (StockPriceDto quote : quotes) {
            if (quote == null
                    || isBlank(quote.symbol())
                    || quote.price() == null
                    || isBlank(quote.sourceName())) {
                continue;
            }

            String symbol = normalizeSymbol(quote.symbol());
            SourceName sourceName = SourceName.valueOf(quote.sourceName());
            LocalDateTime timestamp = quote.dataTimestamp() != null
                    ? LocalDateTime.ofInstant(quote.dataTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);

            MarketInstrument instrument = marketInstrumentRepository
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(symbol, InstrumentType.STOCK)
                    .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(symbol)
                            .instrumentName(symbol)
                            .instrumentType(InstrumentType.STOCK)
                            .sourceName(sourceName)
                            .bistTier(BistTier.OTHER)
                            .stockSector(StockSector.OTHER)
                            .build()));

            java.math.BigDecimal changeRate = dailyChangeService.calculate(
                    instrument,
                    quote.price(),
                    timestamp,
                    sourceName
            );

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.price())
                    .changeRate(changeRate != null ? changeRate : quote.changePercent())
                    .priceTimestamp(timestamp)
                    .sourceName(sourceName)
                    .build());

            putCacheSilently(buildCacheKey(symbol), toDto(instrument, quote.price(), timestamp, quote, changeRate));
        }
    }

    @Transactional
    public void saveYahooHistory(String symbol, List<StockHistoryDto> history) {
        if (isBlank(symbol) || history == null || history.isEmpty()) {
            return;
        }

        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = marketInstrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(normalizedSymbol, InstrumentType.STOCK)
                .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(normalizedSymbol)
                        .instrumentName(normalizedSymbol)
                        .instrumentType(InstrumentType.STOCK)
                        .sourceName(SourceName.YAHOO_FINANCE)
                        .bistTier(BistTier.OTHER)
                        .stockSector(StockSector.OTHER)
                        .build()));

        for (StockHistoryDto item : history) {
            if (item == null || item.priceTimestamp() == null) {
                continue;
            }

            Instant normalizedTimestamp = item.priceTimestamp()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC);

            MarketPriceHistory entity = marketPriceHistoryRepository
                    .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestamp(
                            instrument,
                            IntervalType.ONE_DAY,
                            SourceName.YAHOO_FINANCE,
                            normalizedTimestamp
                    )
                    .orElseGet(() -> MarketPriceHistory.builder()
                            .instrument(instrument)
                            .intervalType(IntervalType.ONE_DAY)
                            .sourceName(SourceName.YAHOO_FINANCE)
                            .priceTimestamp(normalizedTimestamp)
                            .build());

            entity.setOpenPrice(item.openPrice());
            entity.setHighPrice(item.highPrice());
            entity.setLowPrice(item.lowPrice());
            entity.setClosePrice(item.closePrice());
            entity.setVolume(item.volume());

            marketPriceHistoryRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public Page<StockPriceDto> getAll(Pageable pageable, BistTier bistTier) {
        try {
            List<BistTier> tiers = resolveFilterTiers(bistTier);
            String tierClause = tiers != null ? "and mi.bistTier in :tiers " : "";

            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            tierClause +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class
            );
            dataQuery.setParameter("instrumentType", InstrumentType.STOCK);
            if (tiers != null) {
                dataQuery.setParameter("tiers", tiers);
            }
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            List<StockPriceDto> content = dataQuery.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

            TypedQuery<Long> countQuery = entityManager.createQuery(
                    "select count(mi) from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            tierClause,
                    Long.class
            );
            countQuery.setParameter("instrumentType", InstrumentType.STOCK);
            if (tiers != null) {
                countQuery.setParameter("tiers", tiers);
            }
            Long total = countQuery.getSingleResult();

            return new PageImpl<>(content, pageable, total);
        } catch (Exception exception) {
            log.error("Failed to load paged stock data from database.", exception);
            return Page.empty(pageable);
        }
    }

    private List<BistTier> resolveFilterTiers(BistTier bistTier) {
        if (bistTier == null) {
            return null;
        }
        return switch (bistTier) {
            case BIST30  -> List.of(BistTier.BIST30);
            case BIST50  -> List.of(BistTier.BIST30, BistTier.BIST50);
            case BIST100 -> List.of(BistTier.BIST30, BistTier.BIST50, BistTier.BIST100);
            case OTHER   -> List.of(BistTier.OTHER);
        };
    }

    @Transactional(readOnly = true)
    public StockPriceDto getBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        try {
            MarketInstrument instrument = marketInstrumentRepository
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(normalizedSymbol, InstrumentType.STOCK)
                    .orElseThrow(() -> new InstrumentNotFoundException("Stock instrument not found: " + normalizedSymbol));

            MarketPrice latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument)
                    .orElseThrow(() -> new InstrumentNotFoundException("Stock price not found: " + normalizedSymbol));

            StockPriceDto dto = toDto(instrument, latestPrice.getPriceValue(), latestPrice.getPriceTimestamp(), null);
            putCacheSilently(buildCacheKey(normalizedSymbol), dto);
            return dto;
        } catch (InstrumentNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to load stock data for symbol {}.", normalizedSymbol, exception);
            throw new InstrumentNotFoundException("Stock instrument not found: " + normalizedSymbol, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<StockHistoryDto> getHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = marketInstrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(normalizedSymbol, InstrumentType.STOCK)
                .filter(item -> item.getInstrumentType() == InstrumentType.STOCK)
                .orElseThrow(() -> new InstrumentNotFoundException("Stock instrument not found: " + normalizedSymbol));

        Instant from = (startDate != null ? startDate : LocalDate.of(1970, 1, 1))
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        Instant to = (endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC))
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        List<StockHistoryDto> history = marketPriceHistoryRepository
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument,
                        IntervalType.ONE_DAY,
                        SourceName.YAHOO_FINANCE,
                        from,
                        to
                ).stream()
                .map(this::toHistoryDto)
                .toList();

        if (history.isEmpty()) {
            throw new InstrumentNotFoundException("Stock history not found: " + normalizedSymbol);
        }

        return history;
    }

    private StockPriceDto toDto(MarketInstrument instrument) {
        MarketPrice latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument).orElse(null);
        if (latestPrice == null) {
            return null;
        }
        return new StockPriceDto(
                instrument.getInstrumentCode(),
                bistSymbolRegistry.toYahooSymbol(instrument.getInstrumentCode()),
                latestPrice.getPriceValue(),
                resolveChangeRate(instrument, latestPrice),
                null,
                null,
                null,
                null,
                latestPrice.getSourceName().name(),
                latestPrice.getPriceTimestamp().toInstant(ZoneOffset.UTC),
                null,
                instrument.getBistTier() != null ? instrument.getBistTier().name() : null,
                instrument.getStockSector() != null ? instrument.getStockSector().name() : null,
                null
        );
    }

    private StockPriceDto toDto(MarketInstrument instrument,
                                java.math.BigDecimal currentPrice,
                                LocalDateTime dataTimestamp,
                                StockPriceDto cachedSource) {
        return toDto(instrument, currentPrice, dataTimestamp, cachedSource, null);
    }

    private StockPriceDto toDto(MarketInstrument instrument,
                                java.math.BigDecimal currentPrice,
                                LocalDateTime dataTimestamp,
                                StockPriceDto cachedSource,
                                java.math.BigDecimal resolvedChangeRate) {
        String symbol = instrument.getInstrumentCode();
        java.math.BigDecimal changeRate = resolvedChangeRate != null
                ? resolvedChangeRate
                : resolveChangeRate(instrument, currentPrice, dataTimestamp, cachedSource);
        return new StockPriceDto(
                symbol,
                bistSymbolRegistry.toYahooSymbol(symbol),
                currentPrice,
                changeRate,
                cachedSource != null ? cachedSource.previousClose() : null,
                cachedSource != null ? cachedSource.dayHigh() : null,
                cachedSource != null ? cachedSource.dayLow() : null,
                cachedSource != null ? cachedSource.volume() : null,
                cachedSource != null ? cachedSource.sourceName() : SourceName.YAHOO_FINANCE.name(),
                dataTimestamp != null ? dataTimestamp.toInstant(ZoneOffset.UTC) : Instant.now(),
                cachedSource != null ? cachedSource.openPrice() : null,
                instrument.getBistTier() != null ? instrument.getBistTier().name() : null,
                instrument.getStockSector() != null ? instrument.getStockSector().name() : null,
                null
        );
    }

    private java.math.BigDecimal resolveChangeRate(MarketInstrument instrument, MarketPrice latestPrice) {
        if (latestPrice == null) {
            return null;
        }
        java.math.BigDecimal calculated = dailyChangeService.calculate(
                instrument,
                latestPrice.getPriceValue(),
                latestPrice.getPriceTimestamp(),
                latestPrice.getSourceName()
        );
        return calculated != null ? calculated : latestPrice.getChangeRate();
    }

    private java.math.BigDecimal resolveChangeRate(
            MarketInstrument instrument,
            java.math.BigDecimal currentPrice,
            LocalDateTime dataTimestamp,
            StockPriceDto cachedSource
    ) {
        SourceName sourceName = SourceName.YAHOO_FINANCE;
        if (cachedSource != null && cachedSource.sourceName() != null) {
            try {
                sourceName = SourceName.valueOf(cachedSource.sourceName());
            } catch (IllegalArgumentException ignored) {
                sourceName = SourceName.YAHOO_FINANCE;
            }
        }

        java.math.BigDecimal calculated = dailyChangeService.calculate(
                instrument,
                currentPrice,
                dataTimestamp,
                sourceName
        );
        return calculated != null ? calculated : cachedSource != null ? cachedSource.changePercent() : null;
    }

    private StockHistoryDto toHistoryDto(MarketPriceHistory history) {
        return new StockHistoryDto(
                history.getPriceTimestamp(),
                history.getOpenPrice(),
                history.getHighPrice(),
                history.getLowPrice(),
                history.getClosePrice(),
                history.getVolume()
        );
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getStock());
        } catch (Exception exception) {
            log.warn("Failed to write stock cache for key={}", key, exception);
        }
    }

    private String buildCacheKey(String symbol) {
        return "stock:" + symbol;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
