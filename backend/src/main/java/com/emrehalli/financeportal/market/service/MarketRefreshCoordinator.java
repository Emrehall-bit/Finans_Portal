package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketRefreshCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshCoordinator.class);

    private final ProviderOrchestrationService providerOrchestrationService;
    private final MarketRefreshService marketRefreshService;
    private final MarketRefreshProperties marketRefreshProperties;
    private final Clock clock;
    private final Map<DataSource, Instant> lastExecutionTimes = new ConcurrentHashMap<>();

    @Autowired
    public MarketRefreshCoordinator(ProviderOrchestrationService providerOrchestrationService,
                                    MarketRefreshService marketRefreshService,
                                    MarketRefreshProperties marketRefreshProperties) {
        this(providerOrchestrationService, marketRefreshService, marketRefreshProperties, Clock.systemUTC());
    }

    MarketRefreshCoordinator(ProviderOrchestrationService providerOrchestrationService,
                             MarketRefreshService marketRefreshService,
                             MarketRefreshProperties marketRefreshProperties,
                             Clock clock) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.marketRefreshService = marketRefreshService;
        this.marketRefreshProperties = marketRefreshProperties;
        this.clock = clock;
    }

    public void refreshDueProviders() {
        Instant now = clock.instant();
        var availableSources = providerOrchestrationService.availableSources();

        log.info("Market refresh coordinator evaluating providers: sources={}", availableSources);

        for (DataSource source : availableSources) {
            MarketRefreshProperties.ProviderPolicy policy = marketRefreshProperties.policyFor(source).orElse(null);
            if (policy == null || !policy.isEnabled()) {
                log.info("Market provider skipped: source={}, reason=disabled-or-missing-policy", source);
                continue;
            }

            if (!isDue(source, policy, now)) {
                log.info(
                        "Market provider skipped: source={}, reason=not-due, refreshMinutes={}",
                        source,
                        policy.getRefreshMinutes()
                );
                continue;
            }

            refreshProvider(source, now);
        }
    }

    boolean isDue(DataSource source, MarketRefreshProperties.ProviderPolicy policy, Instant now) {
        Instant lastExecutionAt = lastExecutionTimes.get(source);
        if (lastExecutionAt == null) {
            return true;
        }

        return lastExecutionAt.plus(Duration.ofMinutes(Math.max(policy.getRefreshMinutes(), 1L))).isBefore(now)
                || lastExecutionAt.plus(Duration.ofMinutes(Math.max(policy.getRefreshMinutes(), 1L))).equals(now);
    }

    private void refreshProvider(DataSource source, Instant now) {
        log.info("Market provider due refresh started: source={}", source);

        try {
            var results = marketRefreshService.refreshSourceDetailed(source);
            boolean refreshSucceeded = results.stream()
                    .findFirst()
                    .map(result -> {
                        logRefreshResult(source, result);
                        return result.success();
                    })
                    .orElseGet(() -> {
                        log.warn("Market provider refresh returned no result: source={}", source);
                        return false;
                    });

            if (refreshSucceeded) {
                lastExecutionTimes.put(source, now);
            }
        } catch (Exception ex) {
            log.error("Market provider refresh failed unexpectedly: source={}", source, ex);
        }
    }

    private void logRefreshResult(DataSource source, MarketRefreshResult result) {
        if (result.success()) {
            log.info(
                    "Market provider refresh completed: source={}, quoteCount={}",
                    source,
                    result.quoteCount()
            );
            return;
        }

        log.warn(
                "Market provider refresh failed: source={}, error={}",
                source,
                result.errorMessage()
        );
    }
}
