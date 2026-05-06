package com.emrehalli.financeportal.market.api.dto;

import java.util.List;

public record MarketHistoryEnvelopeResponse(
        String historyStatus,
        int pointCount,
        int requiredPointCount,
        String source,
        String message,
        List<MarketHistoryResponse> data
) {
}
