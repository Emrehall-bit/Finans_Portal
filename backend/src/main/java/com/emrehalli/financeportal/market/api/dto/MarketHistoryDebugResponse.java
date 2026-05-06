package com.emrehalli.financeportal.market.api.dto;

import java.time.LocalDate;

public record MarketHistoryDebugResponse(
        String symbol,
        String requestedRange,
        LocalDate resolvedStartDate,
        LocalDate resolvedEndDate,
        int pointCount,
        LocalDate minDate,
        LocalDate maxDate,
        long distinctPriceCount,
        String source
) {
}
