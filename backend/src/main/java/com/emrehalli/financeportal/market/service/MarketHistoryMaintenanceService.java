package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.model.BackfillRunStatus;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketHistoryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryMaintenanceService.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final InstrumentRegistryService instrumentRegistryService;
    private final MarketHistoryService marketHistoryService;
    private final MarketHistoryBackfillService marketHistoryBackfillService;
    private final MarketHistoryBackfillProperties properties;
    private final MarketBackfillStatusService marketBackfillStatusService;
    private final Clock clock;

    private volatile boolean runtimeStarted;
    private volatile Instant startupAt;

    @EventListener(ApplicationReadyEvent.class)
    public void markStarted() {
        runtimeStarted = true;
        startupAt = clock.instant();
    }

    public void runIfDue() {
        if (!properties.isEnabled() || !runtimeStarted || startupAt == null) {
            return;
        }
        if (clock.instant().isBefore(startupAt.plusSeconds(Math.max(properties.getStartupDelaySeconds(), 1L)))) {
            return;
        }
        backfillMissingHistory(false);
    }

    public MarketBackfillJobResult triggerManual(DataSource source, String symbol) {
        return backfillSymbol(source, symbol, true);
    }

    private void backfillMissingHistory(boolean force) {
        int minimumHistoryCount = Math.max(properties.getMinDataPoints(), 1);
        Duration cooldown = Duration.ofMinutes(Math.max(properties.getFailedCooldownMinutes(), 1L));

        for (DataSource source : providerOrchestrationService.availableSources()) {
            List<InstrumentRegistryService.ResolvedMapping> mappings = instrumentRegistryService.resolveMappings(source).mappings();
            for (InstrumentRegistryService.ResolvedMapping mapping : mappings) {
                long historyCount = marketHistoryService.countHistory(mapping.symbol(), source);
                if (!marketBackfillStatusService.isEligible(source, mapping.symbol(), (int) historyCount, minimumHistoryCount, cooldown, force)) {
                    continue;
                }
                backfillSymbol(source, mapping.symbol(), false);
            }
        }
    }

    private MarketBackfillJobResult backfillSymbol(DataSource source, String symbol, boolean force) {
        try {
            log.info("marketBackfillJobStarted providerSource={}, symbol={}", source, symbol);
            marketBackfillStatusService.markRunning(source, symbol);
            int lookbackDays = Math.max(properties.getDefaultLookbackDays(), marketHistoryBackfillService.resolveLookbackDays(source, null));
            List<MarketHistoryPersistenceResult> results = marketHistoryBackfillService.backfill(source, List.of(symbol), lookbackDays);
            int fetchedCount = results.stream().mapToInt(MarketHistoryPersistenceResult::received).sum();
            int savedCount = results.stream().mapToInt(MarketHistoryPersistenceResult::saved).sum();
            long finalCount = marketHistoryService.countHistory(symbol, source);
            LocalDate minDate = marketHistoryService.findMinPriceDate(symbol, source);
            LocalDate maxDate = marketHistoryService.findMaxPriceDate(symbol, source);
            long distinctPriceCount = marketHistoryService.countDistinctClosePrices(symbol, source);
            log.info("marketBackfillCompleted providerSource={}, symbol={}, fetchedCount={}, savedCount={}", source, symbol, fetchedCount, savedCount);
            log.info("Market backfill DB stats: providerSource={}, symbol={}, count={}, minPriceDate={}, maxPriceDate={}, distinctPriceCount={}",
                    source,
                    symbol, finalCount, minDate, maxDate, distinctPriceCount);
            BackfillRunStatus status = savedCount > 0 || finalCount >= properties.getRequiredHistoryPointCount()
                    ? BackfillRunStatus.SUCCESS
                    : BackfillRunStatus.FAILED;
            return marketBackfillStatusService.markCompleted(source, symbol, status, fetchedCount, savedCount, minDate, maxDate,
                    status == BackfillRunStatus.SUCCESS ? "Backfill completed" : "Insufficient history after backfill");
        } catch (Exception ex) {
            log.warn("marketBackfillFailed providerSource={}, symbol={}, error={}", source, symbol, ex.getMessage(), ex);
            return marketBackfillStatusService.markCompleted(source, symbol, BackfillRunStatus.FAILED, 0, 0, null, null, ex.getMessage());
        }
    }
}
