package com.emrehalli.financeportal.market.cache;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MarketCacheTtlPolicy {

    public Duration ttlFor(DataSource source) {
        if (source == null) {
            return allQuotesTtl();
        }

        return switch (source) {
            case EVDS -> Duration.ofHours(6);
            case BINANCE -> Duration.ofMinutes(2);
            case TEFAS -> Duration.ofDays(1);
            case BIST -> Duration.ofDays(1);
            case KAP -> Duration.ofDays(1);
            default -> Duration.ofMinutes(30);
        };
    }

    public Duration allQuotesTtl() {
        return Duration.ofMinutes(30);
    }

    public Duration symbolQuoteTtl() {
        return Duration.ofHours(1);
    }
}
