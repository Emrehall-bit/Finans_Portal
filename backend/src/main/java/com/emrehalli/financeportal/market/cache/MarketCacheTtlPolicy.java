package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class MarketCacheTtlPolicy {

    private static final Logger log = LoggerFactory.getLogger(MarketCacheTtlPolicy.class);

    private final Map<String, Duration> configuredTtls;

    public MarketCacheTtlPolicy(MarketCacheProperties marketCacheProperties) {
        this.configuredTtls = marketCacheProperties.getTtl();
    }

    public Duration ttlFor(DataSource source) {
        if (source == null) {
            return allQuotesTtl();
        }

        Duration ttl = configuredTtls.getOrDefault(source.name(), fallbackTtlFor(source));
        log.info("Market cache ttl resolved: source={}, ttl={}", source, ttl);
        return ttl;
    }

    public Duration allQuotesTtl() {
        Duration ttl = configuredTtls.getOrDefault("ALL", Duration.ofMinutes(30));
        log.info("Market cache ttl resolved: source={}, ttl={}", "ALL", ttl);
        return ttl;
    }

    public Duration symbolQuoteTtl() {
        return allQuotesTtl();
    }

    private Duration fallbackTtlFor(DataSource source) {
        return switch (source) {
            case EVDS -> Duration.ofHours(6);
            case BINANCE -> Duration.ofMinutes(2);
            case BIST -> Duration.ofMinutes(15);
            default -> Duration.ofMinutes(30);
        };
    }
}
