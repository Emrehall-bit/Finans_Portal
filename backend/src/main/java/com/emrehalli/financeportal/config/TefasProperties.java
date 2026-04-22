package com.emrehalli.financeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "market.providers.tefas")
public class TefasProperties {

    private boolean enabled;
    private Api api = new Api();
    private Scheduling scheduling = new Scheduling();
    private Http http = new Http();
    private List<String> funds = new ArrayList<>();

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

    public Scheduling getScheduling() {
        return scheduling;
    }

    public void setScheduling(Scheduling scheduling) {
        this.scheduling = scheduling;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public List<String> getFunds() {
        return funds;
    }

    public void setFunds(List<String> funds) {
        this.funds = funds;
    }

    public String getBaseUrl() {
        return api.getBaseUrl();
    }

    public String getApiKey() {
        return api.getKey();
    }

    public static class Api {
        private String baseUrl;
        private String key;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Scheduling {
        private String refreshCron;

        public String getRefreshCron() {
            return refreshCron;
        }

        public void setRefreshCron(String refreshCron) {
            this.refreshCron = refreshCron;
        }
    }

    public static class Http {
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private Integer retryCount;

        public Integer getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(Integer connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public Integer getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(Integer readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public Integer getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }
    }
}
