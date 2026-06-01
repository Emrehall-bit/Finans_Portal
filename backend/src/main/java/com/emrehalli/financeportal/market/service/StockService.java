package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
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
                            .build()));

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.price())
                    .changeRate(quote.changePercent())
                    .priceTimestamp(timestamp)
                    .sourceName(sourceName)
                    .build());

            putCacheSilently(buildCacheKey(symbol), toDto(instrument, quote.price(), timestamp, quote));
        }
    }

    @Transactional
    public void saveHistory(String symbol, List<StockHistoryDto> history) {
        if (isBlank(symbol) || history == null || history.isEmpty()) {
            return;
        }

        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedSymbol, SourceName.BIST)
                .filter(item -> item.getInstrumentType() == InstrumentType.STOCK)
                .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                        .instrumentCode(normalizedSymbol)
                        .instrumentName(normalizedSymbol)
                        .instrumentType(InstrumentType.STOCK)
                        .sourceName(SourceName.BIST)
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
                            SourceName.IS_YATIRIM,
                            normalizedTimestamp
                    )
                    .orElseGet(() -> MarketPriceHistory.builder()
                            .instrument(instrument)
                            .intervalType(IntervalType.ONE_DAY)
                            .sourceName(SourceName.IS_YATIRIM)
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
    public Page<StockPriceDto> getAll(Pageable pageable) {
        try {
            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class
            );
            dataQuery.setParameter("instrumentType", InstrumentType.STOCK);
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            List<StockPriceDto> content = dataQuery.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

            Long total = entityManager.createQuery(
                            "select count(mi) from MarketInstrument mi " +
                                    "where mi.instrumentType = :instrumentType",
                            Long.class
                    )
                    .setParameter("instrumentType", InstrumentType.STOCK)
                    .getSingleResult();

            return new PageImpl<>(content, pageable, total);
        } catch (Exception exception) {
            log.error("Failed to load paged stock data from database.", exception);
            return Page.empty(pageable);
        }
    }

    @Transactional(readOnly = true)
    public StockPriceDto getBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockPriceDto cached = getCachedStock(normalizedSymbol);
        if (cached != null) {
            return cached;
        }

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
                .findByInstrumentCodeIgnoreCaseAndSourceName(normalizedSymbol, SourceName.BIST)
                .filter(item -> item.getInstrumentType() == InstrumentType.STOCK)
                .orElseThrow(() -> new InstrumentNotFoundException("Stock instrument not found: " + normalizedSymbol));

        Instant from = (startDate != null ? startDate : LocalDate.of(1970, 1, 1))
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        Instant to = (endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC))
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        List<StockHistoryDto> history = marketPriceHistoryRepository
                .findByInstrumentIdAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument.getId(),
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
                latestPrice.getChangeRate(),
                null,
                null,
                null,
                null,
                instrument.getSourceName().name(),
                latestPrice.getPriceTimestamp().toInstant(ZoneOffset.UTC),
                null
        );
    }

    private StockPriceDto toDto(MarketInstrument instrument,
                                java.math.BigDecimal currentPrice,
                                LocalDateTime dataTimestamp,
                                StockPriceDto cachedSource) {
        String symbol = instrument.getInstrumentCode();
        return new StockPriceDto(
                symbol,
                bistSymbolRegistry.toYahooSymbol(symbol),
                currentPrice,
                cachedSource != null ? cachedSource.changePercent() : null,
                cachedSource != null ? cachedSource.previousClose() : null,
                cachedSource != null ? cachedSource.dayHigh() : null,
                cachedSource != null ? cachedSource.dayLow() : null,
                cachedSource != null ? cachedSource.volume() : null,
                instrument.getSourceName().name(),
                dataTimestamp != null ? dataTimestamp.toInstant(ZoneOffset.UTC) : Instant.now(),
                cachedSource != null ? cachedSource.openPrice() : null
        );
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

    private MarketInstrument findStockInstrument(String normalizedSymbol) {
        return marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedSymbol, SourceName.YAHOO_FINANCE)
                .filter(item -> item.getInstrumentType() == InstrumentType.STOCK)
                .orElseThrow(() -> new InstrumentNotFoundException("Stock instrument not found: " + normalizedSymbol));
    }

    private StockPriceDto getCachedStock(String symbol) {
        try {
            return cacheService.get(buildCacheKey(symbol), StockPriceDto.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read stock cache for symbol={}", symbol, exception);
        }
        return null;
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




