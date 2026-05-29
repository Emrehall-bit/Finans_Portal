package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.news.config.NewsStartupSyncProperties;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NewsStartupSyncListener {

    private static final Logger logger = LogManager.getLogger(NewsStartupSyncListener.class);

    private final NewsScheduler newsScheduler;
    private final NewsStartupSyncProperties startupSyncProperties;

    public NewsStartupSyncListener(NewsScheduler newsScheduler, NewsStartupSyncProperties startupSyncProperties) {
        this.newsScheduler = newsScheduler;
        this.startupSyncProperties = startupSyncProperties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void triggerInitialSync() {
        if (!startupSyncProperties.isEnabled()) {
            logger.info("Startup catch-up sync disabled by configuration");
            return;
        }
        logger.info("Scheduling non-blocking startup news sync");
        newsScheduler.runProviderSync(NewsProviderType.CNBC_RSS, "startup");
        newsScheduler.runProviderSync(NewsProviderType.GUARDIAN, "startup");
        newsScheduler.runProviderSync(NewsProviderType.AA_RSS, "startup");
    }
}




