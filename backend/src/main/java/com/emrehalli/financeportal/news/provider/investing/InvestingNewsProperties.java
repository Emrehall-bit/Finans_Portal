package com.emrehalli.financeportal.news.provider.investing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.providers.investing")
public class InvestingNewsProperties {

    private boolean enabled = true;
    private String rssUrl = "https://www.investing.com/rss/news_14.rss";
    private String defaultCategory = "ECONOMY";
    private String defaultLanguage = "en";
    private String defaultRegionScope = "GLOBAL";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRssUrl() {
        return rssUrl;
    }

    public void setRssUrl(String rssUrl) {
        this.rssUrl = rssUrl;
    }

    public String getDefaultCategory() {
        return defaultCategory;
    }

    public void setDefaultCategory(String defaultCategory) {
        this.defaultCategory = defaultCategory;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String getDefaultRegionScope() {
        return defaultRegionScope;
    }

    public void setDefaultRegionScope(String defaultRegionScope) {
        this.defaultRegionScope = defaultRegionScope;
    }
}
