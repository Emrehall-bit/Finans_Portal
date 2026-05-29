package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService;
import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService.DerivationResult;
import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService;
import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService.FetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Runs the commodity history catch-up pipeline once on application startup,
 * in a background thread so it does not delay the application becoming ready.
 *
 * <p>This handles the case where the application was not running at the scheduled
 * nightly time (01:15 UTC). On startup it detects gaps since the last recorded date
 * and fills them â€” identical logic to {@link CommodityHistoryScheduler}.</p>
 *
 * <p>Safe to run repeatedly: duplicate protection in both services ensures no row
 * is written twice regardless of how many times catch-up is triggered.</p>
 *
 * <p>Prerequisite: initial 10-year seed must be done first via the admin endpoints:
 * POST /api/v1/admin/markets/internal-commodities/history/backfill?days=3650, then
 * POST /api/v1/admin/markets/commodities/history/backfill?days=3650.
 * If no history exists yet, catch-up logs a warning and exits â€” it never seeds from scratch.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommodityHistoryStartupRunner {

    private final InternalCommodityHistoryService internalCommodityHistoryService;
    private final CommodityHistoryDerivationService derivationService;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        Thread thread = new Thread(this::runCatchUp, "commodity-history-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private void runCatchUp() {
        log.info("CommodityHistoryStartupRunner: startup catch-up started");

        int sourceSaved = 0;
        try {
            Map<String, FetchResult> sourceResults = internalCommodityHistoryService.catchUp();
            sourceSaved = sourceResults.values().stream().mapToInt(FetchResult::saved).sum();
            int sourceFetched = sourceResults.values().stream().mapToInt(FetchResult::fetched).sum();
            log.info("CommodityHistoryStartupRunner: source catch-up done. fetched={} saved={}", sourceFetched, sourceSaved);
        } catch (Exception e) {
            log.error("CommodityHistoryStartupRunner: source catch-up failed", e);
        }

        try {
            Map<String, DerivationResult> derived = derivationService.catchUp();
            int derivedSaved   = derived.values().stream().mapToInt(DerivationResult::saved).sum();
            int derivedSkipped = derived.values().stream().mapToInt(DerivationResult::skipped).sum();
            log.info("CommodityHistoryStartupRunner: derivation catch-up done. saved={} skipped={}", derivedSaved, derivedSkipped);
        } catch (Exception e) {
            log.error("CommodityHistoryStartupRunner: derivation catch-up failed", e);
        }

        log.info("CommodityHistoryStartupRunner: startup catch-up finished. sourceSaved={}", sourceSaved);
    }
}




