package com.emrehalli.financeportal.news.provider.kap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.providers.kap")
public class KapNewsProperties {

    private boolean enabled = true;
    private String baseUrl = "https://www.kap.org.tr";
    private String defaultCategory = "DISCLOSURE";
    private String defaultLanguage = "tr";
    private String defaultRegionScope = "TR";
    private int maxItems = 25;

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

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }
}
