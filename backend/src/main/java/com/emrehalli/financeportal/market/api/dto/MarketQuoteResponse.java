package com.emrehalli.financeportal.market.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuoteResponse(
        String symbol,
        String displayName,
        String instrumentType,
        BigDecimal price,
        BigDecimal changeRate,
        String currency,
        String source,
        Instant priceTime,
        Instant fetchedAt
) {
}
