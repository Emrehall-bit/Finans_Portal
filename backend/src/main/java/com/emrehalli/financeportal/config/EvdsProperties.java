package com.emrehalli.financeportal.config;

import com.emrehalli.financeportal.market.enums.InstrumentType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "market.providers.evds")
public class EvdsProperties {

    private boolean enabled;
    private Api api = new Api();
    private List<SeriesItem> series = new ArrayList<>();

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

    public List<SeriesItem> getSeries() {
        return series;
    }

    public void setSeries(List<SeriesItem> series) {
        this.series = series;
    }

    public static class Api {
        private String key;
        private String baseUrl;

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

        public String getUrl() {
            return baseUrl;
        }

        public void setUrl(String url) {
            this.baseUrl = url;
        }
    }

    public static class SeriesItem {
        private String fieldName;
        private String seriesCode;
        private String symbol;
        private String name;
        private InstrumentType instrumentType = InstrumentType.UNKNOWN;
        private String currency = "TRY";

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getSeriesCode() {
            return seriesCode;
        }

        public void setSeriesCode(String seriesCode) {
            this.seriesCode = seriesCode;
        }

        public String getEvdsKey() {
            return fieldName;
        }

        public void setEvdsKey(String evdsKey) {
            this.fieldName = evdsKey;
        }

        public String getApiCode() {
            return seriesCode;
        }

        public void setApiCode(String apiCode) {
            this.seriesCode = apiCode;
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
