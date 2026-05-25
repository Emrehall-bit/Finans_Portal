package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.IndexHistoryService;
import com.emrehalli.financeportal.market.service.IndexHistoryService.FetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Nightly pipeline that keeps BIST index price history up to date.
 *
 * <p>Runs at 01:30 UTC — after NYSE/CME close and after the commodity pipeline (01:15 UTC),
 * so all BIST indexes' closing bars are final on Yahoo Finance.</p>
 *
 * <p>Finds the last recorded date per index symbol, computes the gap to yesterday,
 * and fetches only the missing window. Duplicate-safe.</p>
 *
 * <p>Initial 10-year seed: use the admin backfill endpoint —
 * POST /api/v1/admin/markets/indexes/history/backfill?days=3650.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IndexHistoryScheduler {

    private final IndexHistoryService indexHistoryService;

    @Scheduled(cron = "0 30 1 * * *")
    public void runNightlyPipeline() {
        log.info("IndexHistoryScheduler: nightly pipeline started");

        try {
            Map<String, FetchResult> results = indexHistoryService.catchUp();
            int totalFetched = results.values().stream().mapToInt(FetchResult::fetched).sum();
            int totalSaved   = results.values().stream().mapToInt(FetchResult::saved).sum();
            int totalSkipped = results.values().stream().mapToInt(FetchResult::skipped).sum();
            log.info("IndexHistoryScheduler: nightly pipeline finished. fetched={} saved={} skipped={}",
                    totalFetched, totalSaved, totalSkipped);
        } catch (Exception e) {
            log.error("IndexHistoryScheduler: nightly pipeline failed", e);
        }
    }
}



