package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.service.TcmbFxCurrentPriceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TcmbFxCurrentPriceScheduler {

    private final TcmbFxCurrentPriceSyncService tcmbFxCurrentPriceSyncService;
    private final MarketProperties marketProperties;

    @Scheduled(initialDelay = 0, fixedRateString = "${market.providers.tcmb.current-sync-fixed-rate-ms:3600000}")
    public void sync() {
        if (!marketProperties.getProviders().getTcmb().isCurrentSyncEnabled()) {
            log.info("TCMB FX current price sync skipped because current-sync-enabled=false");
            return;
        }

        try {
            log.info("TCMB FX current price sync started");
            tcmbFxCurrentPriceSyncService.syncCurrentPrices();
            log.info("TCMB FX current price sync completed");
        } catch (Exception exception) {
            log.error("TCMB FX current price sync failed", exception);
        }
    }
}
