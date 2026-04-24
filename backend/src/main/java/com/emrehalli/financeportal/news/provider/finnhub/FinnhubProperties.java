package com.emrehalli.financeportal.news.provider.finnhub;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finnhub")
public class FinnhubProperties {

    private boolean enabled = true;
    private Api api = new Api();
    private Sync sync = new Sync();

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
        this.api = api;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public static class Api {
        private String url = "https://finnhub.io/api/v1";
        private String key = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Sync {
        private int defaultDaysBack = 3;

        public int getDefaultDaysBack() {
            return defaultDaysBack;
        }

        public void setDefaultDaysBack(int defaultDaysBack) {
            this.defaultDaysBack = defaultDaysBack;
        }
    }
}
