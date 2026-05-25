package com.emrehalli.financeportal.market.provider.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider-level stock history payload.
 */
public record StockHistoryDto(
        Instant priceTimestamp,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal volume
) {
}



