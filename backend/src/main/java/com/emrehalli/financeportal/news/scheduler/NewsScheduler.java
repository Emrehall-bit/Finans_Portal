package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.common.exception.ProviderRateLimitException;
import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.cnbc.CnbcNewsProperties;
import com.emrehalli.financeportal.news.provider.finnhub.FinnhubProperties;
import com.emrehalli.financeportal.news.provider.investing.InvestingNewsProperties;
import com.emrehalli.financeportal.news.provider.kap.KapNewsProperties;
import com.emrehalli.financeportal.news.provider.reuters.ReutersNewsProperties;
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
    private final FinnhubProperties finnhubProperties;
    private final CnbcNewsProperties cnbcNewsProperties;
    private final ReutersNewsProperties reutersNewsProperties;
    private final InvestingNewsProperties investingNewsProperties;
    private final KapNewsProperties kapNewsProperties;

    public NewsScheduler(
            NewsService newsService,
            FinnhubProperties finnhubProperties,
            CnbcNewsProperties cnbcNewsProperties,
            ReutersNewsProperties reutersNewsProperties,
            InvestingNewsProperties investingNewsProperties,
            KapNewsProperties kapNewsProperties
    ) {
        this.newsService = newsService;
        this.finnhubProperties = finnhubProperties;
        this.cnbcNewsProperties = cnbcNewsProperties;
        this.reutersNewsProperties = reutersNewsProperties;
        this.investingNewsProperties = investingNewsProperties;
        this.kapNewsProperties = kapNewsProperties;
    }

    @PostConstruct
    public void loadOnStartup() {
        logger.info("NewsScheduler started. Loading latest news on startup...");
        runProviderSync(NewsProviderType.CNBC_RSS, "startup");
        runProviderSync(NewsProviderType.REUTERS_RSS, "startup");
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void syncPrimaryProviders() {
        runProviderSync(NewsProviderType.CNBC_RSS, "scheduled");
        runProviderSync(NewsProviderType.REUTERS_RSS, "scheduled");
    }

    @Scheduled(cron = "0 10,40 * * * *")
    public void syncSecondaryRssProviders() {
        runProviderSync(NewsProviderType.AA_RSS, "scheduled");
    }

    @Scheduled(cron = "${news.providers.kap.scheduler-cron:0 0 */2 * * *}")
    public void syncKapProvider() {
        if (!kapNewsProperties.isSchedulerEnabled()) {
            return;
        }
        runProviderSync(NewsProviderType.KAP, "scheduled");
    }

    void runProviderSync(NewsProviderType providerType, String trigger) {
        if (providerType == NewsProviderType.FINNHUB && !finnhubProperties.isEnabled()) {
            logger.info("Skipping Finnhub news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.CNBC_RSS && !cnbcNewsProperties.isEnabled()) {
            logger.info("Skipping CNBC RSS news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.REUTERS_RSS && !reutersNewsProperties.isEnabled()) {
            logger.info("Skipping Reuters RSS news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.INVESTING_RSS && !investingNewsProperties.isEnabled()) {
            logger.info("Skipping Investing RSS news sync because provider is disabled. trigger: {}", trigger);
            return;
        }
        if (providerType == NewsProviderType.KAP && !kapNewsProperties.isEnabled()) {
            logger.info("Skipping KAP news sync because provider is disabled. trigger: {}", trigger);
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
