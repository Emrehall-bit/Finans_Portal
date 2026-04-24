package com.emrehalli.financeportal.market.provider.bist.dto;

import java.math.BigDecimal;

public record BistQuoteResponse(
        String symbol,
        String shortName,
        String longName,
        BigDecimal regularMarketPrice,
        BigDecimal regularMarketChangePercent,
        Long regularMarketTime
) {
}
