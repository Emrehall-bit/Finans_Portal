package com.emrehalli.financeportal.market.provider.evdsmacro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.provider-clients.evds-macro")
public class EvdsMacroProperties {

    private boolean enabled;
    private Api api = new Api();
    private History history = new History();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api == null ? new Api() : api;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history == null ? new History() : history;
    }

    public static class Api {
        private String key;
        private String baseUrl;
        private String macroPath = "/fe";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getMacroPath() {
            return macroPath;
        }

        public void setMacroPath(String macroPath) {
            this.macroPath = macroPath;
        }
    }

    public static class History {
        private int schedulerLookbackDays = 31;
        private int backfillDefaultDays = 365;

        public int getSchedulerLookbackDays() {
            return schedulerLookbackDays;
        }

        public void setSchedulerLookbackDays(int schedulerLookbackDays) {
            this.schedulerLookbackDays = schedulerLookbackDays;
        }

        public int getBackfillDefaultDays() {
            return backfillDefaultDays;
        }

        public void setBackfillDefaultDays(int backfillDefaultDays) {
            this.backfillDefaultDays = backfillDefaultDays;
        }
    }
}
