package com.emrehalli.financeportal.market.provider.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider-level stock price payload.
 */
public record StockPriceDto(
        String symbol,
        String yahooSymbol,
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal previousClose,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume,
        String sourceName,
        Instant dataTimestamp
) {
}
