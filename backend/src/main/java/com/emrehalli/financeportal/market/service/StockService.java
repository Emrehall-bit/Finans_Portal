package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.stock.dto.StockQuoteDto;
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
import java.time.LocalDateTime;
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

    private static final String META_DELIMITER = "||META||";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<StockQuoteDto> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        for (StockQuoteDto quote : quotes) {
            if (quote == null
                    || isBlank(quote.getSymbol())
                    || quote.getCurrentPrice() == null
                    || quote.getSourceName() == null) {
                continue;
            }

            String symbol = normalizeSymbol(quote.getSymbol());
            LocalDateTime timestamp = quote.getDataTimestamp() != null ? quote.getDataTimestamp() : LocalDateTime.now();
            String companyName = defaultString(quote.getCompanyName(), symbol);
            BigDecimal changePercent = quote.getChangePercent();

            MarketInstrument instrument = marketInstrumentRepository.findByInstrumentCodeAndSourceName(symbol, quote.getSourceName())
                    .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(symbol)
                            .instrumentName(encodeInstrumentName(companyName, changePercent))
                            .instrumentType(InstrumentType.STOCK)
                            .sourceName(quote.getSourceName())
                            .build()));

            instrument.setInstrumentName(encodeInstrumentName(companyName, changePercent));
            marketInstrumentRepository.save(instrument);

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.getCurrentPrice())
                    .priceTimestamp(timestamp)
                    .sourceName(quote.getSourceName())
                    .build());

            putCacheSilently(buildCacheKey(symbol), toDto(instrument, quote.getCurrentPrice(), timestamp));
        }
    }

    @Transactional(readOnly = true)
    public Page<StockQuoteDto> getAll(Pageable pageable) {
        try {
            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            "and mi.sourceName = :sourceName " +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class
            );
            dataQuery.setParameter("instrumentType", InstrumentType.STOCK);
            dataQuery.setParameter("sourceName", SourceName.YAHOO_FINANCE);
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            List<StockQuoteDto> content = dataQuery.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

            Long total = entityManager.createQuery(
                            "select count(mi) from MarketInstrument mi " +
                                    "where mi.instrumentType = :instrumentType " +
                                    "and mi.sourceName = :sourceName",
                            Long.class
                    )
                    .setParameter("instrumentType", InstrumentType.STOCK)
                    .setParameter("sourceName", SourceName.YAHOO_FINANCE)
                    .getSingleResult();

            return new PageImpl<>(content, pageable, total);
        } catch (Exception exception) {
            log.error("Failed to load paged stock data from database.", exception);
            return Page.empty(pageable);
        }
    }

    @Transactional(readOnly = true)
    public StockQuoteDto getBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockQuoteDto cached = getCachedStock(normalizedSymbol);
        if (cached != null) {
            return cached;
        }

        try {
            StockQuoteDto dto = marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedSymbol, SourceName.YAHOO_FINANCE)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.STOCK)
                    .map(this::toDto)
                    .orElse(null);

            if (dto != null) {
                putCacheSilently(buildCacheKey(normalizedSymbol), dto);
            }
            return dto;
        } catch (Exception exception) {
            log.error("Failed to load stock data for symbol {}. Returning cached value when available.", normalizedSymbol, exception);
            return getCachedStock(normalizedSymbol);
        }
    }

    private StockQuoteDto toDto(MarketInstrument instrument) {
        Optional<MarketPrice> latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument);
        if (latestPrice.isEmpty()) {
            return null;
        }
        MarketPrice marketPrice = latestPrice.get();
        return toDto(instrument, marketPrice.getPriceValue(), marketPrice.getPriceTimestamp());
    }

    private StockQuoteDto toDto(MarketInstrument instrument, BigDecimal currentPrice, LocalDateTime dataTimestamp) {
        StockMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return StockQuoteDto.builder()
                .symbol(instrument.getInstrumentCode())
                .companyName(metadata.companyName())
                .currentPrice(currentPrice)
                .changePercent(metadata.changePercent())
                .dataTimestamp(dataTimestamp)
                .sourceName(instrument.getSourceName())
                .build();
    }

    private StockQuoteDto getCachedStock(String symbol) {
        try {
            return cacheService.get(buildCacheKey(symbol), StockQuoteDto.class).orElse(null);
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

    private String encodeInstrumentName(String companyName, BigDecimal changePercent) {
        return companyName.trim() + META_DELIMITER + (changePercent != null ? changePercent.toPlainString() : "");
    }

    private StockMetadata decodeInstrumentName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return new StockMetadata("", null);
        }

        String[] parts = instrumentName.split("\\Q" + META_DELIMITER + "\\E", 2);
        if (parts.length < 2) {
            return new StockMetadata(instrumentName, null);
        }

        BigDecimal changePercent = null;
        if (!parts[1].isBlank()) {
            try {
                changePercent = new BigDecimal(parts[1]);
            } catch (NumberFormatException ignored) {
                // Ignore malformed metadata and keep null change percent.
            }
        }
        return new StockMetadata(parts[0], changePercent);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StockMetadata(String companyName, BigDecimal changePercent) {
    }
}
