package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.MarketRefreshCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshScheduler.class);

    private final MarketRefreshCoordinator marketRefreshCoordinator;

    public MarketRefreshScheduler(MarketRefreshCoordinator marketRefreshCoordinator) {
        this.marketRefreshCoordinator = marketRefreshCoordinator;
    }

    @Scheduled(
            initialDelayString = "${market.refresh.initial-delay-ms:0}",
            fixedDelayString = "${market.refresh.scheduler-delay-ms:300000}"
    )
    public void refreshMarketQuotes() {
        try {
            log.info("Market refresh scheduler tick started");
            marketRefreshCoordinator.refreshDueProviders();
        } catch (Exception ex) {
            log.error("Scheduled market refresh failed unexpectedly", ex);
        }
    }
}
