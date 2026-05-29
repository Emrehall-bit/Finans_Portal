package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.kap.KapNewsProperties;
import com.emrehalli.financeportal.news.service.NewsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "news.providers.kap.startup-sync-enabled", havingValue = "true")
public class KapNewsStartupRunner implements ApplicationRunner {

    private static final Logger logger = LogManager.getLogger(KapNewsStartupRunner.class);

    private final NewsService newsService;
    private final KapNewsProperties properties;

    public KapNewsStartupRunner(NewsService newsService, KapNewsProperties properties) {
        this.newsService = newsService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            logger.info("KAP startup sync skipped: provider disabled.");
            return;
        }

        logger.info("KAP startup sync starting. startupLookbackDays: {}", properties.getStartupLookbackDays());
        try {
            newsService.syncProvider(NewsProviderType.KAP);
            logger.info("KAP startup sync completed.");
        } catch (Exception ex) {
            logger.error("KAP startup sync failed. reason: {}", ex.getMessage(), ex);
        }
    }
}




