package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import com.emrehalli.financeportal.market.service.ProviderOrchestrationService;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MarketRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshScheduler.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketRefreshService marketRefreshService;
    private final MarketRefreshProperties marketRefreshProperties;
    private final InstrumentRegistryService instrumentRegistryService;
    private final MarketHistoryBackfillService marketHistoryBackfillService;
    private final Clock clock;

    public MarketRefreshScheduler(ProviderOrchestrationService providerOrchestrationService,
                                  MarketRefreshService marketRefreshService,
                                  MarketRefreshProperties marketRefreshProperties,
                                  InstrumentRegistryService instrumentRegistryService,
                                  MarketHistoryBackfillService marketHistoryBackfillService,
                                  Clock clock) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketRefreshService = marketRefreshService;
        this.marketRefreshProperties = marketRefreshProperties;
        this.instrumentRegistryService = instrumentRegistryService;
        this.marketHistoryBackfillService = marketHistoryBackfillService;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${market.refresh.initial-delay-ms:0}",
            fixedDelayString = "${market.refresh.scheduler-delay-ms:300000}"
    )
    public void refreshMarketQuotes() {
        try {
            log.info("Market refresh scheduler tick started");
            // marketHistoryBackfillService.runIfDue();
            refreshDueProviders();
        } catch (Exception ex) {
            log.error("Scheduled market refresh failed unexpectedly", ex);
        }
    }

    public void refreshDueProviders() {
        Instant now = clock.instant();
        var availableSources = providerOrchestrationService.availableSources();

        log.info("Market refresh scheduler evaluating providers: sources={}", availableSources);

        for (DataSource source : availableSources) {
            MarketRefreshProperties.ProviderPolicy policy = marketRefreshProperties.policyFor(source).orElse(null);
            if (policy == null || !policy.isEnabled()) {
                log.info("Market provider skipped: source={}, reason=disabled-or-missing-policy", source);
                continue;
            }

            List<InstrumentRegistryService.ResolvedMapping> dueMappings = instrumentRegistryService.getDueMappings(source, now);
            if (dueMappings.isEmpty()) {
                log.info("Market provider skipped: source={}, reason=not-due", source);
                continue;
            }

            refreshMappings(source, dueMappings);
        }
    }

    private void refreshMappings(DataSource source, List<InstrumentRegistryService.ResolvedMapping> dueMappings) {
        LinkedHashSet<String> symbols = dueMappings.stream()
                .map(InstrumentRegistryService.ResolvedMapping::symbol)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("Market provider due refresh started: source={}, dueSymbolCount={}", source, symbols.size());

        try {
            var results = marketRefreshService.refreshDetailed(new ProviderFetchRequest(
                    source,
                    List.copyOf(symbols),
                    java.util.Set.of(),
                    null,
                    null,
                    Map.of()
            ));
            logRefreshResults(source, results);
        } catch (Exception ex) {
            log.error("Market provider refresh failed unexpectedly: source={}", source, ex);
            instrumentRegistryService.markRefreshFailure(source, symbols, ex.getMessage());
        }
    }

    private void logRefreshResults(DataSource source, List<MarketRefreshResult> results) {
        if (results == null || results.isEmpty()) {
            log.warn("Market provider refresh returned no result: source={}", source);
            return;
        }

        for (MarketRefreshResult result : results) {
            if (result.success()) {
                log.info("Market provider refresh completed: source={}, quoteCount={}", source, result.quoteCount());
            } else {
                log.warn("Market provider refresh failed: source={}, error={}", source, result.errorMessage());
            }
        }
    }
}
