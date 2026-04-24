package com.emrehalli.financeportal.market.provider;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;

import java.util.List;

public record ProviderFetchResult(
        List<MarketQuote> quotes,
        List<MarketHistoryRecord> historyRecords
) {
    public ProviderFetchResult {
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
        historyRecords = historyRecords == null ? List.of() : List.copyOf(historyRecords);
    }

    public static ProviderFetchResult of(List<MarketQuote> quotes) {
        return new ProviderFetchResult(quotes, List.of());
    }
}
