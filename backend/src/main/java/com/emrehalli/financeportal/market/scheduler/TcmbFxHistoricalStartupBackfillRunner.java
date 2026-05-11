package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.service.TcmbFxHistoricalBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TcmbFxHistoricalStartupBackfillRunner {

    private final TcmbFxHistoricalBackfillService tcmbFxHistoricalBackfillService;
    private final MarketProperties marketProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        if (!marketProperties.getProviders().getTcmb().isStartupBackfillEnabled()) {
            log.info("TCMB FX historical startup backfill skipped because startup-backfill-enabled=false");
            return;
        }

        try {
            log.info("TCMB FX historical startup backfill started");
            TcmbFxHistoricalBackfillService.BackfillSummary summary = tcmbFxHistoricalBackfillService.backfillAll();
            log.info("TCMB FX historical startup backfill completed. rows={}, saved={}, duplicatesSkipped={}, parseOrNullSkipped={}, missingInstruments={}",
                    summary.rows(), summary.saved(), summary.duplicatesSkipped(), summary.parseOrNullSkipped(), summary.missingInstruments());
        } catch (Exception exception) {
            log.error("TCMB FX historical startup backfill failed", exception);
        }
    }
}
