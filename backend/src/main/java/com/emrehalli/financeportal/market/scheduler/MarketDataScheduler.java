package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.provider.tcmb.TcmbMarketDataProvider;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarketDataScheduler {

    private static final Logger logger = LogManager.getLogger(MarketDataScheduler.class);

    private final TcmbMarketDataProvider tcmbMarketDataProvider;
    private final MarketDataCacheService marketDataCacheService;

    public MarketDataScheduler(TcmbMarketDataProvider tcmbMarketDataProvider,
                               MarketDataCacheService marketDataCacheService) {
        this.tcmbMarketDataProvider = tcmbMarketDataProvider;
        this.marketDataCacheService = marketDataCacheService;
    }

    @PostConstruct
    public void loadOnStartup() {
        logger.info("MarketDataScheduler started. Loading TCMB data on startup...");
        refreshTcmbData();
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void refreshTcmbData() {
        try {
            logger.info("Refreshing TCMB market data...");

            List<MarketDataDto> data = tcmbMarketDataProvider.fetchMarketData();

            if (data != null && !data.isEmpty()) {
                logger.info("Fetched TCMB data count: {}", data.size());
                marketDataCacheService.updateTcmbData(data);
                logger.info("TCMB cache updated successfully.");
            } else {
                logger.warn("Fetched TCMB data is null or empty. Cache not updated.");
            }

        } catch (Exception e) {
            logger.error("TCMB cache refresh failed", e);
        }
    }
}