package com.emrehalli.financeportal.news.provider.aa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "news.providers.aa")
public class AaNewsProperties {

    private boolean enabled = true;
    private String rssUrl; // legacy field — kept for backward compat
    private String defaultCategory = "ECONOMY";
    private String defaultLanguage = "tr";
    private String defaultRegionScope = "TR";
    private List<FeedConfig> feeds = new ArrayList<>();

    public static class FeedConfig {
        private String name;
        private String rssUrl;
        private String defaultCategory;
        private int relevanceThreshold = 0;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRssUrl() { return rssUrl; }
        public void setRssUrl(String rssUrl) { this.rssUrl = rssUrl; }
        public String getDefaultCategory() { return defaultCategory; }
        public void setDefaultCategory(String defaultCategory) { this.defaultCategory = defaultCategory; }
        public int getRelevanceThreshold() { return relevanceThreshold; }
        public void setRelevanceThreshold(int relevanceThreshold) { this.relevanceThreshold = relevanceThreshold; }
    }

    /**
     * Returns the effective feed list. If {@code feeds} is configured with at least one valid entry,
     * those feeds are used. Otherwise falls back to a single feed wrapping the legacy {@code rss-url}
     * for backward compatibility.
     */
    public List<FeedConfig> getEffectiveFeeds() {
        List<FeedConfig> validFeeds = feeds.stream()
                .filter(f -> f.getRssUrl() != null && !f.getRssUrl().isBlank())
                .toList();
        if (!validFeeds.isEmpty()) {
            return validFeeds;
        }
        String legacyUrl = (rssUrl != null && !rssUrl.isBlank())
                ? rssUrl
                : "https://www.aa.com.tr/tr/rss/default?cat=ekonomi";
        FeedConfig legacy = new FeedConfig();
        legacy.setName("economy");
        legacy.setRssUrl(legacyUrl);
        legacy.setDefaultCategory(defaultCategory != null ? defaultCategory : "ECONOMY");
        legacy.setRelevanceThreshold(0);
        return List.of(legacy);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRssUrl() { return rssUrl; }
    public void setRssUrl(String rssUrl) { this.rssUrl = rssUrl; }
    public String getDefaultCategory() { return defaultCategory; }
    public void setDefaultCategory(String defaultCategory) { this.defaultCategory = defaultCategory; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public String getDefaultRegionScope() { return defaultRegionScope; }
    public void setDefaultRegionScope(String defaultRegionScope) { this.defaultRegionScope = defaultRegionScope; }
    public List<FeedConfig> getFeeds() { return feeds; }
    public void setFeeds(List<FeedConfig> feeds) { this.feeds = feeds != null ? feeds : new ArrayList<>(); }
}



