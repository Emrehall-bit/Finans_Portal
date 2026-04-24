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
            case EVDS -> Duration.ofMinutes(15);
            case BINANCE -> Duration.ofMinutes(1);
            case TEFAS -> Duration.ofDays(1);
            case BIST -> Duration.ofMinutes(15);
            default -> Duration.ofMinutes(10);
        };
    }

    public Duration allQuotesTtl() {
        return Duration.ofMinutes(10);
    }

    public Duration symbolQuoteTtl() {
        return Duration.ofMinutes(10);
    }
}
