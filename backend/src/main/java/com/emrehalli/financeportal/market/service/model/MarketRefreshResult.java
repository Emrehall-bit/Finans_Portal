package com.emrehalli.financeportal.market.service.model;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;

import java.time.Instant;
import java.util.List;

public record MarketRefreshResult(
        DataSource source,
        boolean success,
        int quoteCount,
        String errorMessage,
        Instant refreshedAt,
        List<MarketQuote> quotes,
        List<MarketHistoryRecord> historyRecords
) {
    public MarketRefreshResult {
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
        historyRecords = historyRecords == null ? List.of() : List.copyOf(historyRecords);
        quoteCount = quotes.size();
    }

    public static MarketRefreshResult success(DataSource source, List<MarketQuote> quotes) {
        return new MarketRefreshResult(source, true, 0, null, Instant.now(), quotes, List.of());
    }

    public static MarketRefreshResult success(DataSource source, ProviderFetchResult fetchResult) {
        return new MarketRefreshResult(
                source,
                true,
                0,
                null,
                Instant.now(),
                fetchResult == null ? List.of() : fetchResult.quotes(),
                fetchResult == null ? List.of() : fetchResult.historyRecords()
        );
    }

    public static MarketRefreshResult failure(DataSource source, String errorMessage) {
        return new MarketRefreshResult(source, false, 0, errorMessage, Instant.now(), List.of(), List.of());
    }
}
