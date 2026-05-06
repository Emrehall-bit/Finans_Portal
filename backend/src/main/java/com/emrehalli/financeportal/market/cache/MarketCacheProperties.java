package com.emrehalli.financeportal.market.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "market.cache")
public class MarketCacheProperties {

    private Map<String, Duration> ttl = new LinkedHashMap<>();

    public Map<String, Duration> getTtl() {
        return ttl;
    }

    public void setTtl(Map<String, Duration> ttl) {
        this.ttl = ttl == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ttl);
    }
}
