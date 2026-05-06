package com.emrehalli.financeportal.market.provider.bist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BistHistoryPoint(
        String providerSymbol,
        String displayName,
        LocalDate priceDate,
        BigDecimal closePrice
) {
}
