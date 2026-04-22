package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.common.MarketDataProviderRegistry;
import com.emrehalli.financeportal.market.service.MarketPersistenceService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarketDataScheduler {

    private static final Logger logger = LogManager.getLogger(MarketDataScheduler.class);

    private final MarketDataProviderRegistry marketDataProviderRegistry;
    private final MarketDataCacheService marketDataCacheService;
    private final MarketPersistenceService marketPersistenceService;

    public MarketDataScheduler(MarketDataProviderRegistry marketDataProviderRegistry,
                               MarketDataCacheService marketDataCacheService,
                               MarketPersistenceService marketPersistenceService) {
        this.marketDataProviderRegistry = marketDataProviderRegistry;
        this.marketDataCacheService = marketDataCacheService;
        this.marketPersistenceService = marketPersistenceService;
    }

    @PostConstruct
    public void loadOnStartup() {
        logger.info("MarketDataScheduler started. Loading market data on startup...");
        refreshMarketData();
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void refreshMarketData() {
        logger.info("Refreshing market data for active providers: {}", marketDataProviderRegistry.getActiveProviderNames());

        List<MarketDataDto> aggregatedSnapshots = new ArrayList<>();

        for (MarketDataProvider provider : marketDataProviderRegistry.getActiveProviders()) {
            try {
                List<MarketDataDto> snapshots = provider.fetchSnapshotData();

                if (snapshots.isEmpty()) {
                    logger.warn("Provider {} returned no market data", provider.getProviderName());
                    marketDataCacheService.updateProviderData(provider.getProviderName(), List.of());
                    continue;
                }

                logger.info("Fetched {} market records from provider {}", snapshots.size(), provider.getProviderName());
                marketDataCacheService.updateProviderData(provider.getProviderName(), snapshots);
                marketPersistenceService.saveSnapshots(snapshots);
                aggregatedSnapshots.addAll(snapshots);
            } catch (Exception e) {
                logger.error("Market data refresh failed for provider {}", provider.getProviderName(), e);
            }
        }

        if (aggregatedSnapshots.isEmpty()) {
            logger.warn("No market data was refreshed from active providers. Aggregate cache will be cleared.");
        }

        marketDataCacheService.updateCurrentData(aggregatedSnapshots);
    }
}
