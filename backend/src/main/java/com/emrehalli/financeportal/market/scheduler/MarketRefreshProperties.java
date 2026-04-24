package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "market.refresh")
public class MarketRefreshProperties {

    private long schedulerDelayMs = 300000L;
    private long initialDelayMs = 0L;
    private Map<String, ProviderPolicy> providers = Map.of();

    public long getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(long schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public Map<String, ProviderPolicy> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderPolicy> providers) {
        this.providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public Optional<ProviderPolicy> policyFor(DataSource source) {
        if (source == null) {
            return Optional.empty();
        }

        String sourceKey = source.name().toLowerCase(Locale.ROOT);
        ProviderPolicy directMatch = providers.get(sourceKey);
        if (directMatch != null) {
            return Optional.of(directMatch);
        }

        if (source == DataSource.KAP) {
            return Optional.ofNullable(providers.get("cap"));
        }

        return Optional.empty();
    }

    public static class ProviderPolicy {

        private boolean enabled = true;
        private long refreshMinutes = 5L;
        private int defaultWindowDays = 7;
        private int batchSize = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getRefreshMinutes() {
            return refreshMinutes;
        }

        public void setRefreshMinutes(long refreshMinutes) {
            this.refreshMinutes = refreshMinutes;
        }

        public int getDefaultWindowDays() {
            return defaultWindowDays;
        }

        public void setDefaultWindowDays(int defaultWindowDays) {
            this.defaultWindowDays = defaultWindowDays;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
