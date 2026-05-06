package com.emrehalli.financeportal.market.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.history.backfill")
public class MarketHistoryBackfillProperties {

    private boolean enabled = true;
    private int minDataPoints = 100;
    private long intervalHours = 24L;
    private int defaultLookbackDays = 365;
    private long startupDelaySeconds = 90L;
    private long failedCooldownMinutes = 30L;
    private int requiredHistoryPointCount = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinDataPoints() {
        return minDataPoints;
    }

    public void setMinDataPoints(int minDataPoints) {
        this.minDataPoints = minDataPoints;
    }

    public long getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(long intervalHours) {
        this.intervalHours = intervalHours;
    }

    public int getDefaultLookbackDays() {
        return defaultLookbackDays;
    }

    public void setDefaultLookbackDays(int defaultLookbackDays) {
        this.defaultLookbackDays = defaultLookbackDays;
    }

    public long getStartupDelaySeconds() {
        return startupDelaySeconds;
    }

    public void setStartupDelaySeconds(long startupDelaySeconds) {
        this.startupDelaySeconds = startupDelaySeconds;
    }

    public long getFailedCooldownMinutes() {
        return failedCooldownMinutes;
    }

    public void setFailedCooldownMinutes(long failedCooldownMinutes) {
        this.failedCooldownMinutes = failedCooldownMinutes;
    }

    public int getRequiredHistoryPointCount() {
        return requiredHistoryPointCount;
    }

    public void setRequiredHistoryPointCount(int requiredHistoryPointCount) {
        this.requiredHistoryPointCount = requiredHistoryPointCount;
    }
}
