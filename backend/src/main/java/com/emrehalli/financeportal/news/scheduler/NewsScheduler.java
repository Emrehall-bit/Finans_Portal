package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.common.exception.ProviderRateLimitException;
import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.investing.InvestingNewsProperties;
import com.emrehalli.financeportal.news.service.NewsService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private static final Logger logger = LogManager.getLogger(NewsScheduler.class);

    private final NewsService newsService;
    private final InvestingNewsProperties investingNewsProperties;

    public NewsScheduler(
            NewsService newsService,
            InvestingNewsProperties investingNewsProperties
    ) {
        this.newsService = newsService;
        this.investingNewsProperties = investingNewsProperties;
    }

    @PostConstruct
    public void loadOnStartup() {
        logger.info("NewsScheduler started. Loading latest news on startup...");
        runProviderSync(NewsProviderType.FINNHUB, "startup");
        runProviderSync(NewsProviderType.INVESTING_RSS, "startup");
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void syncPrimaryProviders() {
        runProviderSync(NewsProviderType.FINNHUB, "scheduled");
        runProviderSync(NewsProviderType.INVESTING_RSS, "scheduled");
    }

    @Scheduled(cron = "0 10,40 * * * *")
    public void syncSecondaryRssProviders() {
        runProviderSync(NewsProviderType.AA_RSS, "scheduled");
    }

    void runProviderSync(NewsProviderType providerType, String trigger) {
        if (providerType == NewsProviderType.INVESTING_RSS && !investingNewsProperties.isEnabled()) {
            logger.info("Skipping Investing RSS news sync because provider is disabled. trigger: {}", trigger);
            return;
        }

        SchedulerLogSupport.Run run = SchedulerLogSupport.start("NewsScheduler." + trigger + "." + providerType.name());
        try {
            logger.info("{} {} news sync started", capitalize(trigger), providerType.name());
            NewsSyncResponseDto result = newsService.syncProvider(providerType);
            int processedCount = result.getFetchedCount();
            int successCount = result.getSavedCount() + result.getExistingCount();
            int failedCount = result.getInvalidCount();
            logger.info(
                    "{} {} sync completed. provider: {}, fetched: {}, saved: {}, existing: {}, invalid: {}",
                    capitalize(trigger),
                    providerType.name(),
                    result.getProvider(),
                    result.getFetchedCount(),
                    result.getSavedCount(),
                    result.getExistingCount(),
                    result.getInvalidCount()
            );
            run.log(logger, processedCount, successCount, failedCount);
        } catch (ProviderRateLimitException e) {
            logger.warn("{} {} sync rate limited: {}", capitalize(trigger), providerType.name(), e.getMessage());
            run.log(logger, 1, 0, 1, e);
        } catch (Exception e) {
            logger.error("{} {} sync failed", capitalize(trigger), providerType.name(), e);
            run.log(logger, 1, 0, 1, e);
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.trim().toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
