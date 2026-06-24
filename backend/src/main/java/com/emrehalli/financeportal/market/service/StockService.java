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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockService {

    private static final String CACHE_KEY_ALL_PREFIX = "stock:all";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;
    private final BistSymbolRegistry bistSymbolRegistry;
    private final MarketDailyChangeService dailyChangeService;

    // -------------------------------------------------------------------------
    // Write path
    // -------------------------------------------------------------------------

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

            String symbol = quote.symbol();
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

            BigDecimal changeRate = calculateChangeFromPreviousClose(quote.price(), quote.previousClose());
            if (changeRate == null) {
                changeRate = dailyChangeService.calculate(instrument, quote.price(), timestamp, sourceName);
            }
            if (changeRate == null) {
                changeRate = quote.changePercent();
            }

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.price())
                    .changeRate(changeRate)
                    .priceTimestamp(timestamp)
                    .sourceName(sourceName)
                    .build());

            putCacheSilently(buildSingleCacheKey(symbol),
                    toDto(instrument, quote.price(), timestamp, quote, changeRate));
        }

        // Invalidate the list cache so the next getAll() rebuilds with fresh data.
        evictListCache();
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
                            normalizedTimestamp)
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

    // -------------------------------------------------------------------------
    // Read path — list
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<StockPriceDto> getAll(Pageable pageable, BistTier bistTier) {
        boolean isFirstPage = pageable.getOffset() == 0;
        String listCacheKey = buildListCacheKey(bistTier);

        if (isFirstPage) {
            List<StockPriceDto> cached = cacheService.getList(listCacheKey, StockPriceDto.class);
            if (!cached.isEmpty()) {
                long total = cached.size();
                List<StockPriceDto> page = cached.stream()
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .toList();
                return new PageImpl<>(page, pageable, total);
            }
        }

        try {
            List<BistTier> tiers = resolveFilterTiers(bistTier);
            String tierClause = tiers != null ? "and mi.bistTier in :tiers " : "";

            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            tierClause +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class);
            dataQuery.setParameter("instrumentType", InstrumentType.STOCK);
            if (tiers != null) {
                dataQuery.setParameter("tiers", tiers);
            }

            // For the first-page cache path we fetch ALL matching instruments so we can
            // cache the complete list. For subsequent pages we apply the pageable directly.
            if (!isFirstPage) {
                dataQuery.setFirstResult((int) pageable.getOffset());
                dataQuery.setMaxResults(pageable.getPageSize());
            }

            List<MarketInstrument> instruments = dataQuery.getResultList();

            List<StockPriceDto> allContent = buildDtoListBatch(instruments);

            TypedQuery<Long> countQuery = entityManager.createQuery(
                    "select count(mi) from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            tierClause,
                    Long.class);
            countQuery.setParameter("instrumentType", InstrumentType.STOCK);
            if (tiers != null) {
                countQuery.setParameter("tiers", tiers);
            }
            long total = countQuery.getSingleResult();

            if (isFirstPage && !allContent.isEmpty()) {
                putCacheSilently(listCacheKey, allContent);
                List<StockPriceDto> page = allContent.stream()
                        .limit(pageable.getPageSize())
                        .toList();
                return new PageImpl<>(page, pageable, total);
            }

            return new PageImpl<>(allContent, pageable, total);

        } catch (Exception exception) {
            log.error("Failed to load paged stock data from database.", exception);
            return Page.empty(pageable);
        }
    }

    // -------------------------------------------------------------------------
    // Read path — single
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public StockPriceDto getBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockPriceDto cached = getCachedQuote(normalizedSymbol);
        if (cached != null) {
            return cached;
        }

        try {
            MarketInstrument instrument = marketInstrumentRepository
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(normalizedSymbol, InstrumentType.STOCK)
                    .orElseThrow(() -> new InstrumentNotFoundException(
                            "Stock instrument not found: " + normalizedSymbol));

            MarketPrice latestPrice = marketPriceRepository
                    .findTopByInstrumentOrderByPriceTimestampDesc(instrument)
                    .orElseThrow(() -> new InstrumentNotFoundException(
                            "Stock price not found: " + normalizedSymbol));

            StockPriceDto metadata = resolveQuoteMetadata(instrument, latestPrice);
            StockPriceDto dto = toDto(instrument, latestPrice.getPriceValue(),
                    latestPrice.getPriceTimestamp(), metadata);
            putCacheSilently(buildSingleCacheKey(normalizedSymbol), dto);
            return dto;

        } catch (InstrumentNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to load stock data for symbol {}.", normalizedSymbol, exception);
            throw new InstrumentNotFoundException(
                    "Stock instrument not found: " + normalizedSymbol, exception);
        }
    }

    // -------------------------------------------------------------------------
    // Read path — history
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StockHistoryDto> getHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = marketInstrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(normalizedSymbol, InstrumentType.STOCK)
                .filter(item -> item.getInstrumentType() == InstrumentType.STOCK)
                .orElseThrow(() -> new InstrumentNotFoundException(
                        "Stock instrument not found: " + normalizedSymbol));

        Instant from = (startDate != null ? startDate : LocalDate.of(1970, 1, 1))
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = (endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC))
                .atStartOfDay().toInstant(ZoneOffset.UTC);

        List<StockHistoryDto> history = marketPriceHistoryRepository
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument, IntervalType.ONE_DAY, SourceName.YAHOO_FINANCE, from, to)
                .stream()
                .map(this::toHistoryDto)
                .toList();

        if (history.isEmpty()) {
            history = marketPriceHistoryRepository
                    .findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                            instrument, IntervalType.ONE_DAY, from, to)
                    .stream()
                    .map(this::toHistoryDto)
                    .toList();
        }

        if (history.isEmpty()) {
            throw new InstrumentNotFoundException("Stock history not found: " + normalizedSymbol);
        }

        return history;
    }

    // -------------------------------------------------------------------------
    // Batch DTO assembly (list path — no per-instrument DB or Redis calls)
    // -------------------------------------------------------------------------

    private List<StockPriceDto> buildDtoListBatch(List<MarketInstrument> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }

        List<Long> ids = instruments.stream()
                .map(MarketInstrument::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, MarketPrice> priceById = marketPriceRepository
                .findLatestPricesForInstruments(ids)
                .stream()
                .filter(p -> p.getInstrument() != null && p.getInstrument().getId() != null)
                .collect(Collectors.toMap(
                        p -> p.getInstrument().getId(),
                        Function.identity(),
                        (a, b) -> a));

        Map<Long, MarketPriceHistory> latestHistoryById = marketPriceHistoryRepository
                .findLatestDailyHistoriesForInstruments(ids, SourceName.YAHOO_FINANCE.name())
                .stream()
                .filter(h -> h.getInstrument() != null && h.getInstrument().getId() != null)
                .collect(Collectors.toMap(
                        h -> h.getInstrument().getId(),
                        Function.identity(),
                        (a, b) -> a));

        Map<Long, MarketPriceHistory> previousCloseById = marketPriceHistoryRepository
                .findPreviousClosesForStockInstruments(ids, SourceName.YAHOO_FINANCE.name())
                .stream()
                .filter(h -> h.getInstrument() != null && h.getInstrument().getId() != null)
                .collect(Collectors.toMap(
                        h -> h.getInstrument().getId(),
                        Function.identity(),
                        (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toDtoBatch(
                        instrument,
                        priceById.get(instrument.getId()),
                        latestHistoryById.get(instrument.getId()),
                        previousCloseById.get(instrument.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private StockPriceDto toDtoBatch(MarketInstrument instrument,
                                     MarketPrice latestPrice,
                                     MarketPriceHistory latestHistory,
                                     MarketPriceHistory previousHistory) {
        if (latestPrice == null) {
            return null;
        }

        String symbol = instrument.getInstrumentCode();
        BigDecimal previousClose = previousHistory != null ? previousHistory.getClosePrice() : null;
        BigDecimal changeRate = firstDecimal(
                calculateChangeFromPreviousClose(latestPrice.getPriceValue(), previousClose),
                latestPrice.getChangeRate());

        return new StockPriceDto(
                symbol,
                bistSymbolRegistry.toYahooSymbol(symbol),
                latestPrice.getPriceValue(),
                changeRate,
                previousClose,
                latestHistory != null ? latestHistory.getHighPrice() : null,
                latestHistory != null ? latestHistory.getLowPrice() : null,
                latestHistory != null && latestHistory.getVolume() != null
                        ? latestHistory.getVolume().longValue() : null,
                latestPrice.getSourceName().name(),
                latestPrice.getPriceTimestamp().toInstant(ZoneOffset.UTC),
                latestHistory != null ? latestHistory.getOpenPrice() : null,
                instrument.getBistTier() != null ? instrument.getBistTier().name() : null,
                instrument.getStockSector() != null ? instrument.getStockSector().name() : null,
                null
        );
    }

    // -------------------------------------------------------------------------
    // Single-instrument DTO assembly (detail path — uses Redis for enrichment)
    // -------------------------------------------------------------------------

    private StockPriceDto toDto(MarketInstrument instrument,
                                BigDecimal currentPrice,
                                LocalDateTime dataTimestamp,
                                StockPriceDto cachedSource) {
        return toDto(instrument, currentPrice, dataTimestamp, cachedSource, null);
    }

    private StockPriceDto toDto(MarketInstrument instrument,
                                BigDecimal currentPrice,
                                LocalDateTime dataTimestamp,
                                StockPriceDto cachedSource,
                                BigDecimal resolvedChangeRate) {
        String symbol = instrument.getInstrumentCode();
        BigDecimal changeRate = resolvedChangeRate != null
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
                null);
    }

    private StockPriceDto resolveQuoteMetadata(MarketInstrument instrument, MarketPrice latestPrice) {
        StockPriceDto cachedSource = getCachedQuote(instrument.getInstrumentCode());
        MarketPriceHistory latestHistory = marketPriceHistoryRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                        instrument, IntervalType.ONE_DAY, SourceName.YAHOO_FINANCE)
                .orElse(null);
        MarketPriceHistory previousHistory = latestHistory != null
                ? marketPriceHistoryRepository
                        .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                                instrument, IntervalType.ONE_DAY, SourceName.YAHOO_FINANCE,
                                latestHistory.getPriceTimestamp())
                        .orElse(null)
                : null;

        return new StockPriceDto(
                instrument.getInstrumentCode(),
                bistSymbolRegistry.toYahooSymbol(instrument.getInstrumentCode()),
                latestPrice.getPriceValue(),
                firstDecimal(
                        calculateChangeFromPreviousClose(
                                latestPrice.getPriceValue(),
                                cachedSource != null ? cachedSource.previousClose() : null),
                        cachedSource != null ? cachedSource.changePercent() : latestPrice.getChangeRate()),
                firstDecimal(
                        cachedSource != null ? cachedSource.previousClose() : null,
                        previousHistory != null ? previousHistory.getClosePrice() : null),
                firstDecimal(
                        cachedSource != null ? cachedSource.dayHigh() : null,
                        latestHistory != null ? latestHistory.getHighPrice() : null),
                firstDecimal(
                        cachedSource != null ? cachedSource.dayLow() : null,
                        latestHistory != null ? latestHistory.getLowPrice() : null),
                firstLong(
                        cachedSource != null ? cachedSource.volume() : null,
                        latestHistory != null ? latestHistory.getVolume() : null),
                cachedSource != null && cachedSource.sourceName() != null
                        ? cachedSource.sourceName() : latestPrice.getSourceName().name(),
                latestPrice.getPriceTimestamp().toInstant(ZoneOffset.UTC),
                firstDecimal(
                        cachedSource != null ? cachedSource.openPrice() : null,
                        latestHistory != null ? latestHistory.getOpenPrice() : null),
                instrument.getBistTier() != null ? instrument.getBistTier().name() : null,
                instrument.getStockSector() != null ? instrument.getStockSector().name() : null,
                cachedSource != null ? cachedSource.sharesOutstanding() : null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BigDecimal resolveChangeRate(MarketInstrument instrument,
                                         BigDecimal currentPrice,
                                         LocalDateTime dataTimestamp,
                                         StockPriceDto cachedSource) {
        SourceName sourceName = SourceName.YAHOO_FINANCE;
        if (cachedSource != null && cachedSource.sourceName() != null) {
            try {
                sourceName = SourceName.valueOf(cachedSource.sourceName());
            } catch (IllegalArgumentException ignored) {
                sourceName = SourceName.YAHOO_FINANCE;
            }
        }

        BigDecimal previousCloseChange = calculateChangeFromPreviousClose(
                currentPrice,
                cachedSource != null ? cachedSource.previousClose() : null);
        if (previousCloseChange != null) {
            return previousCloseChange;
        }

        BigDecimal calculated = dailyChangeService.calculate(instrument, currentPrice, dataTimestamp, sourceName);
        if (calculated != null) {
            return calculated;
        }

        return cachedSource != null ? cachedSource.changePercent() : null;
    }

    private BigDecimal calculateChangeFromPreviousClose(BigDecimal currentPrice, BigDecimal previousClose) {
        if (currentPrice == null || previousClose == null
                || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPrice
                .subtract(previousClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, 4, java.math.RoundingMode.HALF_UP);
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

    private StockHistoryDto toHistoryDto(MarketPriceHistory history) {
        return new StockHistoryDto(
                history.getPriceTimestamp(),
                history.getOpenPrice(),
                history.getHighPrice(),
                history.getLowPrice(),
                history.getClosePrice(),
                history.getVolume());
    }

    private BigDecimal firstDecimal(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private Long firstLong(Long primary, BigDecimal fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback != null ? fallback.longValue() : null;
    }

    private StockPriceDto getCachedQuote(String symbol) {
        try {
            return cacheService.get(buildSingleCacheKey(symbol), StockPriceDto.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read stock cache for symbol={}", symbol, exception);
            return null;
        }
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getStock());
        } catch (Exception exception) {
            log.warn("Failed to write stock cache for key={}", key, exception);
        }
    }

    private void evictListCache() {
        try {
            cacheService.evictByPattern(CACHE_KEY_ALL_PREFIX + "*");
        } catch (Exception exception) {
            log.warn("Failed to evict stock list cache", exception);
        }
    }

    private String buildSingleCacheKey(String symbol) {
        return "stock:" + symbol;
    }

    private String buildListCacheKey(BistTier bistTier) {
        return bistTier == null
                ? CACHE_KEY_ALL_PREFIX
                : CACHE_KEY_ALL_PREFIX + ":" + bistTier.name();
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
