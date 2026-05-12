package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Query service for market price lookups.
 */
public interface MarketQueryService {

    Optional<MarketSnapshot> findBySymbol(String symbol);

    Optional<MarketSnapshot> findBySymbol(String symbol, InstrumentType instrumentType);

    List<HistoricalPrice> getHistory(String symbol, SourceName sourceName, LocalDate from, LocalDate to);

    /**
     * Current market snapshot.
     */
    record MarketSnapshot(
            String symbol,
            String displayName,
            BigDecimal price,
            BigDecimal changeRate,
            String source,
            String instrumentType,
            String currency,
            LocalDateTime fetchedAt
    ) {
    }

    /**
     * Historical market price point.
     */
    record HistoricalPrice(
            String symbol,
            LocalDate priceDate,
            BigDecimal closePrice
    ) {
    }
}
