package com.emrehalli.financeportal.market.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MarketCacheTtlPolicy {

    public Duration allQuotesTtl() {
        return Duration.ofMinutes(10);
    }

    public Duration symbolQuoteTtl() {
        return Duration.ofMinutes(10);
    }
}