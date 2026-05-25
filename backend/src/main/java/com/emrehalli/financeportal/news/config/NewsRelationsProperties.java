package com.emrehalli.financeportal.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "news.relations")
public class NewsRelationsProperties {

    private String mode = "conservative";
    private boolean legacyEnabled = false;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isLegacyEnabled() {
        return legacyEnabled;
    }

    public void setLegacyEnabled(boolean legacyEnabled) {
        this.legacyEnabled = legacyEnabled;
    }

    public boolean isConservativeMode() {
        return !"legacy".equalsIgnoreCase(mode);
    }
}



