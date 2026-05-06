package com.emrehalli.financeportal.market.service.model;

import com.emrehalli.financeportal.market.domain.enums.DataSource;

import java.time.LocalDate;

public record MarketBackfillJobResult(
        DataSource providerSource,
        String symbol,
        BackfillRunStatus status,
        int fetchedCount,
        int savedCount,
        LocalDate minDate,
        LocalDate maxDate,
        int retryCount,
        String message
) {
}
