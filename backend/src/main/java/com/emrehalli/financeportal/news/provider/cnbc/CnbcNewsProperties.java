package com.emrehalli.financeportal.news.provider.cnbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "news.providers.cnbc")
public class CnbcNewsProperties {

    private boolean enabled = true;
    private String rssUrl = "https://www.cnbc.com/id/100003114/device/rss/rss.html";
    private List<String> feedUrls = List.of("https://www.cnbc.com/id/100003114/device/rss/rss.html");
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

    public List<String> getFeedUrls() {
        if (feedUrls == null || feedUrls.isEmpty()) {
            return rssUrl == null || rssUrl.isBlank() ? List.of() : List.of(rssUrl);
        }
        return feedUrls;
    }

    public void setFeedUrls(List<String> feedUrls) {
        this.feedUrls = feedUrls;
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




