package com.emrehalli.financeportal.market.api.dto;

public record MarketHistoryBackfillResponse(
        String source,
        int lookbackDays,
        int received,
        int saved,
        int skippedDuplicate
) {
}
