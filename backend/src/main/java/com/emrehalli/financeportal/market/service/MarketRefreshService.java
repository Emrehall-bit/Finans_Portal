package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshService.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketCacheService marketCacheService;
    private final MarketHistoryService marketHistoryService;

    public MarketRefreshService(ProviderOrchestrationService providerOrchestrationService,
                                MarketCacheService marketCacheService,
                                MarketHistoryService marketHistoryService) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketCacheService = marketCacheService;
        this.marketHistoryService = marketHistoryService;
    }

    public List<MarketQuote> refreshAll() {
        return successfulQuotes(refreshDetailed(ProviderFetchRequest.all()));
    }

    public List<MarketQuote> refreshSource(DataSource source) {
        return successfulQuotes(refreshDetailed(ProviderFetchRequest.forSource(source)));
    }

    public List<MarketRefreshResult> refreshSourceDetailed(DataSource source) {
        return refreshDetailed(ProviderFetchRequest.forSource(source));
    }

    public List<MarketQuote> refresh(ProviderFetchRequest request) {
        return successfulQuotes(refreshDetailed(request));
    }

    public List<MarketRefreshResult> refreshDetailed(ProviderFetchRequest request) {
        List<MarketRefreshResult> results = providerOrchestrationService.fetchQuoteResults(request);

        results.stream()
                .filter(MarketRefreshResult::success)
                .forEach(result -> marketCacheService.putSourceQuotes(result.source(), result.quotes()));

        results.stream()
                .filter(MarketRefreshResult::success)
                .forEach(result -> marketHistoryService.persistHistory(result.source(), result.historyRecords()));

        List<MarketQuote> aggregateQuotes = marketCacheService.rebuildAllQuotes(
                providerOrchestrationService.availableSources()
        );

        int processedQuoteCount = results.stream()
                .filter(MarketRefreshResult::success)
                .mapToInt(MarketRefreshResult::quoteCount)
                .sum();

        int historyRecordCount = results.stream()
                .filter(MarketRefreshResult::success)
                .mapToInt(result -> result.historyRecords().size())
                .sum();

        long failedProviderCount = results.stream()
                .filter(result -> !result.success())
                .count();

        log.info(
                "Market refresh completed: processedQuoteCount={}, historyRecordCount={}, aggregateQuoteCount={}, failedProviderCount={}",
                processedQuoteCount,
                historyRecordCount,
                aggregateQuotes.size(),
                failedProviderCount
        );

        return results;
    }

    private List<MarketQuote> successfulQuotes(List<MarketRefreshResult> results) {
        return results.stream()
                .filter(MarketRefreshResult::success)
                .flatMap(result -> result.quotes().stream())
                .toList();
    }
}
