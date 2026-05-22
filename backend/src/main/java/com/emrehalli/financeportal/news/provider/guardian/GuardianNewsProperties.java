package com.emrehalli.financeportal.news.provider.guardian;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news.providers.guardian")
public class GuardianNewsProperties {

    private boolean enabled = true;
    private String baseUrl = "https://content.guardianapis.com/search";
    private String apiKey;
    private String defaultCategory = "ECONOMY";
    private String defaultLanguage = "en";
    private String defaultRegionScope = "GLOBAL";

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

    public boolean isOperationallyEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
