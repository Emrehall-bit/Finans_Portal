package com.emrehalli.financeportal.market.provider.bist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "market.providers.bist")
public class BistProviderProperties {

    private boolean enabled;
    private String providerMode = "YAHOO_LOW_FREQUENCY";
    private int batchSize = 5;
    private long requestDelayMs = 1500L;
    private long cooldownMinutesOnRateLimit = 60L;
    private String fallbackSource = "BIST_DELAYED";
    private List<String> symbols = new ArrayList<>();
    private Yahoo yahoo = new Yahoo();
    private Delayed delayed = new Delayed();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderMode() {
        return providerMode;
    }

    public void setProviderMode(String providerMode) {
        this.providerMode = providerMode;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getRequestDelayMs() {
        return requestDelayMs;
    }

    public void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public long getCooldownMinutesOnRateLimit() {
        return cooldownMinutesOnRateLimit;
    }

    public void setCooldownMinutesOnRateLimit(long cooldownMinutesOnRateLimit) {
        this.cooldownMinutesOnRateLimit = cooldownMinutesOnRateLimit;
    }

    public String getFallbackSource() {
        return fallbackSource;
    }

    public void setFallbackSource(String fallbackSource) {
        this.fallbackSource = fallbackSource;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols == null ? new ArrayList<>() : symbols;
    }

    public Yahoo getYahoo() {
        return yahoo;
    }

    public void setYahoo(Yahoo yahoo) {
        this.yahoo = yahoo == null ? new Yahoo() : yahoo;
    }

    public Delayed getDelayed() {
        return delayed;
    }

    public void setDelayed(Delayed delayed) {
        this.delayed = delayed == null ? new Delayed() : delayed;
    }

    public static class Yahoo {
        private boolean enabled;
        private String baseUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Delayed {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
