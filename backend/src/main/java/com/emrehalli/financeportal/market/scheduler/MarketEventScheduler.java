package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.provider.common.MarketEventProvider;
import com.emrehalli.financeportal.market.provider.common.MarketEventProviderRegistry;
import com.emrehalli.financeportal.market.service.MarketEventPersistenceService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarketEventScheduler {

    private static final Logger logger = LogManager.getLogger(MarketEventScheduler.class);

    private final MarketEventProviderRegistry marketEventProviderRegistry;
    private final MarketEventPersistenceService marketEventPersistenceService;

    public MarketEventScheduler(MarketEventProviderRegistry marketEventProviderRegistry,
                                MarketEventPersistenceService marketEventPersistenceService) {
        this.marketEventProviderRegistry = marketEventProviderRegistry;
        this.marketEventPersistenceService = marketEventPersistenceService;
    }

    @PostConstruct
    public void loadOnStartup() {
        logger.info("MarketEventScheduler started. Loading market events on startup...");
        refreshMarketEvents();
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void refreshMarketEvents() {
        logger.info("Refreshing market events for active providers: {}", marketEventProviderRegistry.getActiveProviderNames());

        for (MarketEventProvider provider : marketEventProviderRegistry.getActiveProviders()) {
            try {
                List<MarketEventDto> events = provider.fetchRecentEvents();

                if (events.isEmpty()) {
                    logger.warn("Event provider {} returned no market events", provider.getProviderName());
                    continue;
                }

                logger.info("Fetched {} market events from provider {}", events.size(), provider.getProviderName());
                marketEventPersistenceService.saveEvents(events);
            } catch (Exception e) {
                logger.error("Market event refresh failed for provider {}", provider.getProviderName(), e);
            }
        }
    }
}
