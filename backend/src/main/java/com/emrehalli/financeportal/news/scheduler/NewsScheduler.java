package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.service.NewsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private static final Logger logger = LogManager.getLogger(NewsScheduler.class);

    private final NewsService newsService;

    public NewsScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void syncFinnhubNews() {
        try {
            logger.info("Scheduled Finnhub news sync started");
            NewsSyncResponseDto result = newsService.syncProvider(NewsProviderType.FINNHUB);
            logger.info("Scheduled Finnhub sync completed. provider: {}, fetched: {}, saved: {}",
                    result.getProvider(), result.getFetchedCount(), result.getSavedCount());
        } catch (Exception e) {
            logger.error("Scheduled Finnhub sync failed", e);
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void syncBloombergHtNews() {
        try {
            logger.info("Scheduled Bloomberg HT news sync started");
            NewsSyncResponseDto result = newsService.syncProvider(NewsProviderType.BLOOMBERG_HT);
            logger.info("Scheduled Bloomberg HT sync completed. provider: {}, fetched: {}, saved: {}",
                    result.getProvider(), result.getFetchedCount(), result.getSavedCount());
        } catch (Exception e) {
            logger.error("Scheduled Bloomberg HT sync failed", e);
        }
    }
}
