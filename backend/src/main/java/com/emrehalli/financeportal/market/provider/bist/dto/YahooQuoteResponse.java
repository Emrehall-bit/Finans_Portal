package com.emrehalli.financeportal.market.provider.bist.dto;

import java.util.List;

public record YahooQuoteResponse(
        QuoteResponse quoteResponse
) {
    public record QuoteResponse(
            List<BistQuoteResponse> result
    ) {
    }
}
