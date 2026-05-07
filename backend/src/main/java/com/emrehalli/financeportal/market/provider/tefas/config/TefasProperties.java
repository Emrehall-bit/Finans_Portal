package com.emrehalli.financeportal.market.provider.tefas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.providers.tefas")
public class TefasProperties {

    private boolean enabled;
    private String baseUrl = "https://www.tefas.gov.tr";
    private String currentQuotePath = "/api/funds/fonGnlBlgSiraliGetir";
    private String historyPath = "/api/funds/fonGecmisVerisiGetir";
    private String currency = "TRY";
    private int rateLimitPerMinute = 6;
    private History history = new History();

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

    public String getHistoryPath() {
        return historyPath;
    }

    public void setHistoryPath(String historyPath) {
        this.historyPath = historyPath;
    }

    public String getCurrentQuotePath() {
        return currentQuotePath;
    }

    public void setCurrentQuotePath(String currentQuotePath) {
        this.currentQuotePath = currentQuotePath;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history == null ? new History() : history;
    }

    public static class History {
        private int backfillDefaultDays = 365;

        public int getBackfillDefaultDays() {
            return backfillDefaultDays;
        }

        public void setBackfillDefaultDays(int backfillDefaultDays) {
            this.backfillDefaultDays = backfillDefaultDays;
        }
    }
}
