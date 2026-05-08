package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MarketRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshService.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketCacheService marketCacheService;
    private final MarketHistoryService marketHistoryService;
    private final InstrumentRegistryService instrumentRegistryService;
    private final SymbolNormalizer symbolNormalizer;

    public MarketRefreshService(ProviderOrchestrationService providerOrchestrationService,
                                MarketCacheService marketCacheService,
                                MarketHistoryService marketHistoryService,
                                InstrumentRegistryService instrumentRegistryService,
                                SymbolNormalizer symbolNormalizer) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketCacheService = marketCacheService;
        this.marketHistoryService = marketHistoryService;
        this.instrumentRegistryService = instrumentRegistryService;
        this.symbolNormalizer = symbolNormalizer;
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

        results.stream()
                .filter(MarketRefreshResult::success)
                .forEach(result -> updateRegistryStatusForSuccess(result, resolveAttemptedMappings(request, result.source())));

        results.stream()
                .filter(result -> !result.success())
                .forEach(result -> markAttemptedMappingsFailed(resolveAttemptedMappings(request, result.source()), result.errorMessage()));

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

    private List<InstrumentRegistryService.ResolvedMapping> resolveAttemptedMappings(ProviderFetchRequest request, DataSource source) {
        List<InstrumentRegistryService.ResolvedMapping> mappings = instrumentRegistryService.resolveMappings(source).mappings();
        if (request == null || !request.hasSymbolFilter()) {
            return mappings;
        }

        Set<String> filteredSymbols = request.symbols().stream()
                .map(symbolNormalizer::normalize)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return mappings.stream()
                .filter(mapping -> filteredSymbols.contains(mapping.symbol()))
                .toList();
    }

    private void updateRegistryStatusForSuccess(MarketRefreshResult result,
                                                List<InstrumentRegistryService.ResolvedMapping> attemptedMappings) {
        Set<String> refreshedSymbols = new LinkedHashSet<>();
        result.quotes().stream()
                .map(MarketQuote::symbol)
                .forEach(refreshedSymbols::add);
        result.historyRecords().stream()
                .map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::symbol)
                .forEach(refreshedSymbols::add);

        Map<String, InstrumentRegistryService.ResolvedMapping> mappingBySymbol = new LinkedHashMap<>();
        attemptedMappings.forEach(mapping -> mappingBySymbol.putIfAbsent(mapping.symbol(), mapping));

        for (InstrumentRegistryService.ResolvedMapping mapping : mappingBySymbol.values()) {
            if (refreshedSymbols.contains(mapping.symbol())) {
                instrumentRegistryService.markRefreshSuccess(
                        mapping.mappingId(),
                        result.refreshedAt() == null ? Instant.now() : result.refreshedAt()
                );
            } else {
                instrumentRegistryService.markRefreshFailed(mapping.mappingId(), "No data returned during refresh");
            }
        }
    }

    private void markAttemptedMappingsFailed(List<InstrumentRegistryService.ResolvedMapping> attemptedMappings, String reason) {
        if (attemptedMappings == null || attemptedMappings.isEmpty()) {
            return;
        }
        String failureReason = reason == null || reason.isBlank() ? "Refresh failed" : reason;
        attemptedMappings.stream()
                .map(InstrumentRegistryService.ResolvedMapping::mappingId)
                .distinct()
                .forEach(mappingId -> instrumentRegistryService.markRefreshFailed(mappingId, failureReason));
    }

    private List<MarketQuote> successfulQuotes(List<MarketRefreshResult> results) {
        return results.stream()
                .filter(MarketRefreshResult::success)
                .flatMap(result -> result.quotes().stream())
                .toList();
    }
}
