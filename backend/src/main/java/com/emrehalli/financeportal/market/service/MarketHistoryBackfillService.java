package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketHistoryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryBackfillService.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketHistoryService marketHistoryService;
    private final EvdsProperties evdsProperties;

    public MarketHistoryBackfillService(ProviderOrchestrationService providerOrchestrationService,
                                        MarketHistoryService marketHistoryService,
                                        EvdsProperties evdsProperties) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketHistoryService = marketHistoryService;
        this.evdsProperties = evdsProperties;
    }

    public List<MarketHistoryPersistenceResult> backfill(DataSource source, Integer days) {
        return backfill(source, List.of(), days);
    }

    public List<MarketHistoryPersistenceResult> backfill(DataSource source, List<String> symbols, Integer days) {
        int lookbackDays = resolveLookbackDays(source, days);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(lookbackDays - 1L, 0L));

        log.info("Market history backfill started: source={}, lookbackDays={}, symbolCount={}", source, lookbackDays, symbols == null ? 0 : symbols.size());

        List<MarketRefreshResult> results = providerOrchestrationService.fetchQuoteResults(
                new ProviderFetchRequest(
                        source,
                        symbols == null ? List.of() : symbols,
                        java.util.Set.of(),
                        startDate,
                        endDate,
                        java.util.Map.of()
                )
        );

        List<MarketHistoryPersistenceResult> persistenceResults = results.stream()
                .filter(MarketRefreshResult::success)
                .map(result -> marketHistoryService.persistHistory(result.source(), result.historyRecords()))
                .toList();

        log.info("Market history backfill completed: source={}, lookbackDays={}, symbolCount={}", source, lookbackDays, symbols == null ? 0 : symbols.size());
        return persistenceResults;
    }

    public int resolveLookbackDays(DataSource source, Integer requestedDays) {
        if (requestedDays != null && requestedDays > 0) {
            return requestedDays;
        }

        if (source == DataSource.EVDS) {
            return Math.max(evdsProperties.getHistory().getBackfillDefaultDays(), 1);
        }

        return 365;
    }
}
