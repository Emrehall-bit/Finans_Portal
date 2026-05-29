package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.IndexHistoryService;
import com.emrehalli.financeportal.market.service.IndexHistoryService.FetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Runs the BIST index history catch-up pipeline once on application startup,
 * in a background thread so it does not delay the application becoming ready.
 *
 * <p>Handles the case where the application was not running at the scheduled
 * nightly time (01:30 UTC). On startup it detects gaps since the last recorded date
 * and fills them â€” identical logic to {@link IndexHistoryScheduler}.</p>
 *
 * <p>Safe to run repeatedly: duplicate protection ensures no row is written twice.</p>
 *
 * <p>Prerequisite: initial 10-year seed must be done first via the admin endpoint:
 * POST /api/v1/admin/markets/indexes/history/backfill?days=3650.
 * If no history exists yet, catch-up logs a warning and exits â€” it never seeds from scratch.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IndexHistoryStartupRunner {

    private final IndexHistoryService indexHistoryService;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        Thread thread = new Thread(this::runCatchUp, "index-history-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private void runCatchUp() {
        log.info("IndexHistoryStartupRunner: startup catch-up started");
        try {
            Map<String, FetchResult> results = indexHistoryService.catchUp();
            int totalFetched = results.values().stream().mapToInt(FetchResult::fetched).sum();
            int totalSaved   = results.values().stream().mapToInt(FetchResult::saved).sum();
            int totalSkipped = results.values().stream().mapToInt(FetchResult::skipped).sum();
            log.info("IndexHistoryStartupRunner: startup catch-up finished. fetched={} saved={} skipped={}",
                    totalFetched, totalSaved, totalSkipped);
        } catch (Exception e) {
            log.error("IndexHistoryStartupRunner: startup catch-up failed", e);
        }
    }
}




