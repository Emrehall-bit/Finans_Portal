package com.emrehalli.financeportal.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "news.notification")
public class NewsNotificationProperties {

    private boolean enabled = false;
    private int minImportanceScore = 70;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinImportanceScore() {
        return minImportanceScore;
    }

    public void setMinImportanceScore(int minImportanceScore) {
        this.minImportanceScore = minImportanceScore;
    }
}

