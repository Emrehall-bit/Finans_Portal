package com.emrehalli.financeportal.market.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Generic market quote response for INDEX and COMMODITY endpoints.
 */
public record MarketQuoteResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changeRate,
        String source,
        LocalDateTime updatedAt,
        String instrumentType,
        String displayUnit,
        String currency
) {
}




