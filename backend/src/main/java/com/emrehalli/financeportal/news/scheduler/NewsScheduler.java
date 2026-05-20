package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.common.exception.ProviderRateLimitException;
import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.cnbc.CnbcNewsProperties;
import com.emrehalli.financeportal.news.provider.kap.KapNewsProperties;
import com.emrehalli.financeportal.news.provider.world.WorldNewsApiProperties;
import com.emrehalli.financeportal.news.service.NewsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsScheduler {

    private static final Logger logger = LogManager.getLogger(NewsScheduler.class);

    private final NewsService newsService;
    private final CnbcNewsProperties cnbcNewsProperties;
    private final KapNewsProperties kapNewsProperties;
    private final WorldNewsApiProperties worldNewsApiProperties;

    public NewsScheduler(
            NewsService newsService,
            CnbcNewsProperties cnbcNewsProperties,
            KapNewsProperties kapNewsProperties,
            WorldNewsApiProperties worldNewsApiProperties
    ) {
        this.newsService = newsService;
        this.cnbcNewsProperties = cnbcNewsProperties;
        this.kapNewsProperties = kapNewsProperties;
        this.worldNewsApiProperties = worldNewsApiProperties;
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void syncPrimaryProviders() {
        runProviderSync(NewsProviderType.CNBC_RSS, "scheduled");
    }

    @Scheduled(cron = "0 10,40 * * * *")
    public void syncSecondaryRssProviders() {
        runProviderSync(NewsProviderType.AA_RSS, "scheduled");
    }

    @Scheduled(
            fixedDelayString = "#{${news.providers.world-news-api.sync-rate-hours:6} * 3600000}",
            initialDelayString = "#{${news.providers.world-news-api.sync-rate-hours:6} * 3600000}"
    )
    public void syncWorldNewsApiProvider() {
        if (!worldNewsApiProperties.isSchedulerEnabled()) {
            return;
        }
        runProviderSync(NewsProviderType.WORLD_NEWS_API, "scheduled");
    }

    @Scheduled(cron = "${news.providers.kap.scheduler-cron:0 0 */2 * * *}")
    public void syncKapProvider() {
        if (!kapNewsProperties.isSchedulerEnabled()) {
            return;
        }
        runProviderSync(NewsProviderType.KAP, "scheduled");
    }

    void runProviderSync(NewsProviderType providerType, String trigger) {
        if (providerType == NewsProviderType.CNBC_RSS && !cnbcNewsProperties.isEnabled()) {
            logger.info("Skipping CNBC RSS news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.KAP && !kapNewsProperties.isEnabled()) {
            logger.info("Skipping KAP news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.WORLD_NEWS_API
                && (!worldNewsApiProperties.isEnabled() || worldNewsApiProperties.getApiKey() == null || worldNewsApiProperties.getApiKey().isBlank())) {
            logger.info("Skipping World News API sync because provider is disabled or api key missing. trigger: {}", trigger);
            return;
        }

        SchedulerLogSupport.Run run = SchedulerLogSupport.start("NewsScheduler." + trigger + "." + providerType.name());
        try {
            if (providerType == NewsProviderType.CNBC_RSS) {
                logger.info("Scheduler invoking CNBC_RSS. trigger: {}, enabled: {}, feedUrlCount: {}, feedUrls: {}",
                        trigger,
                        cnbcNewsProperties.isEnabled(),
                        cnbcNewsProperties.getFeedUrls().size(),
                        cnbcNewsProperties.getFeedUrls());
            }
            if (providerType == NewsProviderType.WORLD_NEWS_API) {
                logger.info("Scheduler invoking WORLD_NEWS_API. trigger: {}, enabled: {}, maxItemsPerSync: {}, schedulerEnabled: {}, syncRateHours: {}",
                        trigger,
                        worldNewsApiProperties.isEnabled(),
                        worldNewsApiProperties.getMaxItemsPerSync(),
                        worldNewsApiProperties.isSchedulerEnabled(),
                        worldNewsApiProperties.getSyncRateHours());
            }
            logger.info("{} {} news sync started", capitalize(trigger), providerType.name());
            NewsSyncResponseDto result = "startup".equalsIgnoreCase(trigger)
                    ? newsService.syncProviderOnStartup(providerType)
                    : newsService.syncProvider(providerType);
            int processedCount = result.getFetchedCount();
            int successCount = result.getSavedCount() + result.getExistingCount();
            int failedCount = result.getInvalidCount();
            logger.info(
                    "{} {} sync completed. provider: {}, startupSync: {}, fetched: {}, saved: {}, existing: {}, invalid: {}, duplicateSkipped: {}, skippedFullContentNotAvailable: {}",
                    capitalize(trigger),
                    providerType.name(),
                    result.getProvider(),
                    result.getStartupSync(),
                    result.getFetchedCount(),
                    result.getSavedCount(),
                    result.getExistingCount(),
                    result.getInvalidCount(),
                    result.getDuplicateSkipped(),
                    result.getSkippedFullContentNotAvailable()
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

    void runWorldNewsApiStartupSync() {
        if (!worldNewsApiProperties.isStartupEnabled()) {
            logger.info("Skipping WORLD_NEWS_API startup sync because startup-enabled is false");
            return;
        }
        runProviderSync(NewsProviderType.WORLD_NEWS_API, "startup");
    }
}
