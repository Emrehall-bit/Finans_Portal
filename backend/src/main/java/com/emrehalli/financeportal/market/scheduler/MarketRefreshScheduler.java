package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.service.MarketRefreshCoordinator;
import com.emrehalli.financeportal.market.service.MarketHistoryMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshScheduler.class);

    private final MarketRefreshCoordinator marketRefreshCoordinator;
    private final MarketHistoryMaintenanceService marketHistoryMaintenanceService;

    public MarketRefreshScheduler(MarketRefreshCoordinator marketRefreshCoordinator,
                                  MarketHistoryMaintenanceService marketHistoryMaintenanceService) {
        this.marketRefreshCoordinator = marketRefreshCoordinator;
        this.marketHistoryMaintenanceService = marketHistoryMaintenanceService;
    }

    @Scheduled(
            initialDelayString = "${market.refresh.initial-delay-ms:0}",
            fixedDelayString = "${market.refresh.scheduler-delay-ms:300000}"
    )
    public void refreshMarketQuotes() {
        try {
            log.info("Market refresh scheduler tick started");
            marketHistoryMaintenanceService.runIfDue();
            marketRefreshCoordinator.refreshDueProviders();
        } catch (Exception ex) {
            log.error("Scheduled market refresh failed unexpectedly", ex);
        }
    }
}
