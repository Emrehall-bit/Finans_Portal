package com.emrehalli.financeportal.market.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryResponse(
        String symbol,
        LocalDate priceDate,
        BigDecimal closePrice,
        String source,
        String currency
) {
}
