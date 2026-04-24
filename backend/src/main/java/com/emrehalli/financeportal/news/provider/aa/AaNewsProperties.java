package com.emrehalli.financeportal.news.provider.aa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.providers.aa")
public class AaNewsProperties {

    private boolean enabled = true;
    private String rssUrl = "https://www.aa.com.tr/tr/rss/default?cat=ekonomi";
    private String defaultCategory = "ECONOMY";
    private String defaultLanguage = "tr";
    private String defaultRegionScope = "TR";

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
