package com.emrehalli.financeportal.market.service.model;

import com.emrehalli.financeportal.market.domain.enums.DataSource;

public record MarketHistoryPersistenceResult(
        DataSource source,
        int received,
        int saved,
        int skippedDuplicate
) {
}
