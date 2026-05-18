package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService;
import com.emrehalli.financeportal.market.service.CommodityHistoryDerivationService.DerivationResult;
import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService;
import com.emrehalli.financeportal.market.service.InternalCommodityHistoryService.FetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Nightly pipeline that keeps commodity price history up to date.
 *
 * <p>Runs at 01:15 UTC — well after NYSE/CME close, so yesterday's gold/silver bars
 * are final on Yahoo Finance. Two sequential steps:</p>
 * <ol>
 *   <li>Catch up GOLD_USD / SILVER_USD INTERNAL history from Yahoo Finance (GC=F, SI=F)</li>
 *   <li>Derive GRAM_ALTIN, CEYREK_ALTIN, YARIM_ALTIN, TAM_ALTIN, CUMHURIYET_ALTINI,
 *       GUMUS_GRAM from the freshly filled source + existing TCMB USDTRY history</li>
 * </ol>
 *
 * <p>Step 2 is independent: if step 1 fails, step 2 still runs and may derive the days
 * already covered by source history. Both steps are duplicate-safe.</p>
 *
 * <p>Initial 10-year seed: use the admin backfill endpoints —
 * POST /api/v1/admin/markets/internal-commodities/history/backfill?days=3650, then
 * POST /api/v1/admin/markets/commodities/history/backfill?days=3650.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommodityHistoryScheduler {

    private final InternalCommodityHistoryService internalCommodityHistoryService;
    private final CommodityHistoryDerivationService derivationService;

    @Scheduled(cron = "0 15 1 * * *")
    public void runNightlyPipeline() {
        log.info("CommodityHistoryScheduler: nightly pipeline started");

        int sourceSaved = 0;
        try {
            Map<String, FetchResult> sourceResults = internalCommodityHistoryService.catchUp();
            sourceSaved = sourceResults.values().stream().mapToInt(FetchResult::saved).sum();
            int sourceFetched = sourceResults.values().stream().mapToInt(FetchResult::fetched).sum();
            log.info("CommodityHistoryScheduler: source catch-up completed. fetched={} saved={}", sourceFetched, sourceSaved);
        } catch (Exception e) {
            log.error("CommodityHistoryScheduler: source catch-up failed", e);
        }

        try {
            Map<String, DerivationResult> derived = derivationService.catchUp();
            int derivedSaved   = derived.values().stream().mapToInt(DerivationResult::saved).sum();
            int derivedSkipped = derived.values().stream().mapToInt(DerivationResult::skipped).sum();
            log.info("CommodityHistoryScheduler: derivation catch-up completed. saved={} skipped={}", derivedSaved, derivedSkipped);
        } catch (Exception e) {
            log.error("CommodityHistoryScheduler: derivation catch-up failed", e);
        }

        log.info("CommodityHistoryScheduler: nightly pipeline finished. sourceSaved={}", sourceSaved);
    }
}
