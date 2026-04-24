package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderOrchestrationService.class);

    private final List<MarketDataProvider> providers;

    public ProviderOrchestrationService(List<MarketDataProvider> providers) {
        this.providers = providers;
    }

    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetchQuoteResults(request).stream()
                .filter(MarketRefreshResult::success)
                .flatMap(result -> result.quotes().stream())
                .toList();
    }

    public List<MarketRefreshResult> fetchQuoteResults(ProviderFetchRequest request) {
        log.info("Market provider orchestration started: providerCount={}, sourceFilter={}", providers.size(), request == null ? null : request.source());

        return providers.stream()
                .filter(provider -> {
                    boolean supported = provider.supports(request);
                    if (!supported) {
                        log.info("Market provider skipped by supports: providerSource={}, requestSource={}", provider.source(), request == null ? null : request.source());
                    }
                    return supported;
                })
                .map(provider -> fetchProviderQuotes(provider, request))
                .toList();
    }

    public List<DataSource> availableSources() {
        return providers.stream()
                .map(MarketDataProvider::source)
                .distinct()
                .toList();
    }

    private MarketRefreshResult fetchProviderQuotes(MarketDataProvider provider, ProviderFetchRequest request) {
        DataSource source = provider.source();
        log.info("Market provider refresh started: source={}", source);

        try {
            ProviderFetchResult providerResult = provider.fetch(request);
            MarketRefreshResult result = MarketRefreshResult.success(source, providerResult);

            log.info(
                    "Market provider refresh completed: source={}, quoteCount={}, historyRecordCount={}",
                    source,
                    result.quoteCount(),
                    result.historyRecords().size()
            );
            return result;
        } catch (Exception ex) {
            log.warn(
                    "Market provider refresh failed: source={}, error={}",
                    source,
                    ex.getMessage(),
                    ex
            );
            return MarketRefreshResult.failure(source, ex.getMessage());
        }
    }
}
