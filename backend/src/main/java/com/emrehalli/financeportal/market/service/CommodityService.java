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
import com.emrehalli.financeportal.market.provider.commodity.CommoditySymbolRegistry;
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
import java.util.Optional;

/**
 * Service for COMMODITY instrument persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommodityService {

    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceRepository priceRepository;
    private final CacheService cacheService;
    private final MarketProperties props;
    private final CommoditySymbolRegistry symbolRegistry;

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
                    .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                    .orElseGet(() -> instrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(code)
                            .instrumentName(symbolRegistry.toDisplayName(code))
                            .instrumentType(InstrumentType.COMMODITY)
                            .sourceName(sourceName)
                            .build()));

            priceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(quote.price())
                    .changeRate(quote.changePercent())
                    .priceTimestamp(timestamp)
                    .sourceName(sourceName)
                    .build());

            putCacheSilently(buildCacheKey(code), toResponse(instrument, quote.price(), quote.changePercent(), timestamp));
        }
    }

    @Transactional(readOnly = true)
    public List<MarketQuoteResponse> getAll() {
        return instrumentRepository
                .findAllByInstrumentTypeAndSourceName(InstrumentType.COMMODITY, SourceName.YAHOO_FINANCE)
                .stream()
                .map(this::toResponseFromInstrument)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketQuoteResponse getBySymbol(String symbol) {
        String code = symbol.toUpperCase(Locale.ROOT);
        Optional<MarketQuoteResponse> cached = cacheService.get(buildCacheKey(code), MarketQuoteResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        MarketInstrument instrument = instrumentRepository
                .findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(code, InstrumentType.COMMODITY)
                .orElseThrow(() -> new InstrumentNotFoundException("Commodity not found: " + code));

        return toResponseFromInstrument(instrument);
    }

    private MarketQuoteResponse toResponseFromInstrument(MarketInstrument instrument) {
        MarketPrice latest = priceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument).orElse(null);
        if (latest == null) {
            return null;
        }
        return toResponse(instrument, latest.getPriceValue(), latest.getChangeRate(), latest.getPriceTimestamp());
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
                InstrumentType.COMMODITY.name()
        );
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getCommodity());
        } catch (Exception e) {
            log.warn("Failed to write commodity cache for key={}", key, e);
        }
    }

    private String buildCacheKey(String code) {
        return "commodity:" + code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
