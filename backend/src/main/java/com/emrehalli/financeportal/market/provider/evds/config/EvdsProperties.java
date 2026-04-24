package com.emrehalli.financeportal.market.provider.evds.config;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "market.providers.evds")
public class EvdsProperties {

    private boolean enabled;
    private Api api = new Api();
    private History history = new History();
    private List<SeriesConfig> series = new ArrayList<>();

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

    public List<SeriesConfig> getSeries() {
        return series;
    }

    public void setSeries(List<SeriesConfig> series) {
        this.series = series == null ? new ArrayList<>() : series;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history == null ? new History() : history;
    }

    public static class Api {
        private String key;
        private String url;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class History {
        private int schedulerLookbackDays = 7;
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

    public static class SeriesConfig {
        private String evdsKey;
        private String apiCode;
        private String symbol;
        private String name;
        private InstrumentType instrumentType;
        private String currency;

        public String getEvdsKey() {
            return evdsKey;
        }

        public void setEvdsKey(String evdsKey) {
            this.evdsKey = evdsKey;
        }

        public String getApiCode() {
            return apiCode;
        }

        public void setApiCode(String apiCode) {
            this.apiCode = apiCode;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public InstrumentType getInstrumentType() {
            return instrumentType;
        }

        public void setInstrumentType(InstrumentType instrumentType) {
            this.instrumentType = instrumentType;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}
