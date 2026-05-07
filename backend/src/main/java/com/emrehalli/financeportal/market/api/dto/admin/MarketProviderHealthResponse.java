package com.emrehalli.financeportal.market.api.dto.admin;

import java.time.Instant;

public record MarketProviderHealthResponse(
        String source,
        String circuitBreakerState,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int failedMappingCount,
        int totalMappingCount
) {
}
