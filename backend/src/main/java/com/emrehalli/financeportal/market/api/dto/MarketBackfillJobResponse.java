package com.emrehalli.financeportal.market.api.dto;

import java.time.LocalDate;

public record MarketBackfillJobResponse(
        String providerSource,
        String symbol,
        String status,
        int fetchedCount,
        int savedCount,
        LocalDate minDate,
        LocalDate maxDate,
        int retryCount,
        String message
) {
}
