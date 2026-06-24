package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.InstrumentNotFoundException;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.index.IndexSymbolRegistry;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Service for INDEX instrument persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexService {

    private static final String CACHE_KEY_ALL = "index:all";

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceRepository priceRepository;
    private final CacheService cacheService;
    private final MarketProperties props;
    private final IndexSymbolRegistry symbolRegistry;
    private final MarketDailyChangeService dailyChangeService;

    @Transactional
    public void saveAll(List<StockPriceDto> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        for (StockPriceDto quote : quotes) {
            if (quote == null || isBlank(quote.symbol()) || quote.price() == null) {
                continue;
            }

            String code = quote.symbol().toUpperCase(Locale.ROOT);
            SourceName sourceName = SourceName.YAHOO_FINANCE;
            LocalDateTime timestamp = quote.dataTimestamp() != null
                    ? LocalDateTime.ofInstant(quote.dataTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);

            MarketInstrument instrument = instrumentRepository
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.INDEX)
                    .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(code)
                            .instrumentName(symbolRegistry.toDisplayName(code))
                            .instrumentType(InstrumentType.INDEX)
                            .sourceName(sourceName)
                            .build()));

            java.math.BigDecimal changeRate = dailyChangeService.calculate(
                    instrument,
                    quote.price(),
                    timestamp,
                    sourceName
            );

            priceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.price())
                    .changeRate(changeRate != null ? changeRate : quote.changePercent())
                    .priceTimestamp(timestamp)
                    .sourceName(sourceName)
                    .build());

            putCacheSilently(buildCacheKey(code), toResponse(instrument, quote.price(), changeRate != null ? changeRate : quote.changePercent(), timestamp));
        }
        evictCacheSilently(CACHE_KEY_ALL);
    }

    @Transactional(readOnly = true)
    public List<MarketQuoteResponse> getAll() {
        List<MarketQuoteResponse> cached = cacheService.getList(CACHE_KEY_ALL, MarketQuoteResponse.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<MarketQuoteResponse> responses = instrumentRepository
                .findAllByInstrumentTypeAndSourceName(InstrumentType.INDEX, SourceName.YAHOO_FINANCE)
                .stream()
                .map(this::toResponseFromInstrument)
                .filter(Objects::nonNull)
                .toList();
        putCacheSilently(CACHE_KEY_ALL, responses);
        return responses;
    }

    @Transactional(readOnly = true)
    public MarketQuoteResponse getBySymbol(String symbol) {
        String code = symbol.toUpperCase(Locale.ROOT);
        MarketQuoteResponse cached = getCachedQuote(code);
        if (cached != null) {
            return cached;
        }

        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.INDEX)
                .orElseThrow(() -> new InstrumentNotFoundException("Index not found: " + code));

        MarketQuoteResponse response = toResponseFromInstrument(instrument);
        if (response != null) {
            putCacheSilently(buildCacheKey(code), response);
        }
        return response;
    }

    private MarketQuoteResponse toResponseFromInstrument(MarketInstrument instrument) {
        MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument).orElse(null);
        if (latest == null) {
            return null;
        }
        java.math.BigDecimal calculated = dailyChangeService.calculate(
                instrument,
                latest.getPriceValue(),
                latest.getPriceTimestamp(),
                latest.getSourceName()
        );
        return toResponse(instrument, latest.getPriceValue(), calculated != null ? calculated : latest.getChangeRate(), latest.getPriceTimestamp());
    }

    private MarketQuoteResponse toResponse(MarketInstrument instrument, java.math.BigDecimal price,
                                           java.math.BigDecimal changeRate, LocalDateTime updatedAt) {
        return new MarketQuoteResponse(
                instrument.getInstrumentCode(),
                instrument.getInstrumentName(),
                price,
                changeRate,
                SourceName.YAHOO_FINANCE.name(),
                updatedAt,
                InstrumentType.INDEX.name(),
                "POINT",
                null
        );
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getIndex());
        } catch (Exception e) {
            log.warn("Failed to write index cache for key={}", key, e);
        }
    }

    private MarketQuoteResponse getCachedQuote(String code) {
        try {
            return cacheService.get(buildCacheKey(code), MarketQuoteResponse.class).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to read index cache for key={}", buildCacheKey(code), e);
            return null;
        }
    }

    private void evictCacheSilently(String key) {
        try {
            cacheService.evict(key);
        } catch (Exception e) {
            log.warn("Failed to evict index cache for key={}", key, e);
        }
    }

    private String buildCacheKey(String code) {
        return "index:" + code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

