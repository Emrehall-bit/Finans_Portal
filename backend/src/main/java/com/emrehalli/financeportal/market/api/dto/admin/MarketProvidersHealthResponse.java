package com.emrehalli.financeportal.market.api.dto.admin;

import java.util.List;

public record MarketProvidersHealthResponse(
        List<MarketProviderHealthResponse> providers
) {
}
