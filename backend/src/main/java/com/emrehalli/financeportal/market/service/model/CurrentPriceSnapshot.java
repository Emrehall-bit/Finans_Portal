package com.emrehalli.financeportal.market.service.model;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CurrentPriceSnapshot(
        String symbol,
        String displayName,
        InstrumentType instrumentType,
        BigDecimal price,
        BigDecimal changeRate,
        String currency,
        DataSource source,
        Instant priceTime,
        Instant fetchedAt,
        MarketPriceStatus priceStatus,
        Instant lastUpdatedAt,
        boolean priceAvailable
) {

    public static CurrentPriceSnapshot unavailable(String symbol) {
        return new CurrentPriceSnapshot(
                symbol,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                MarketPriceStatus.UNAVAILABLE,
                null,
                false
        );
    }
}
