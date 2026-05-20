package com.emrehalli.financeportal.news.provider.world;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.providers.world-news-api")
public class WorldNewsApiProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.worldnewsapi.com";
    private String apiKey = "";
    private String language = "en";
    private int maxItemsPerSync = 4;
    private String categories = "business";
    private int minContentLength = 500;
    private boolean schedulerEnabled = true;
    private boolean startupEnabled = false;
    private int syncRateHours = 6;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 20000;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMaxItemsPerSync() {
        return maxItemsPerSync;
    }

    public void setMaxItemsPerSync(int maxItemsPerSync) {
        this.maxItemsPerSync = maxItemsPerSync;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public int getMinContentLength() {
        return minContentLength;
    }

    public void setMinContentLength(int minContentLength) {
        this.minContentLength = minContentLength;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public boolean isStartupEnabled() {
        return startupEnabled;
    }

    public void setStartupEnabled(boolean startupEnabled) {
        this.startupEnabled = startupEnabled;
    }

    public int getSyncRateHours() {
        return syncRateHours;
    }

    public void setSyncRateHours(int syncRateHours) {
        this.syncRateHours = syncRateHours;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
