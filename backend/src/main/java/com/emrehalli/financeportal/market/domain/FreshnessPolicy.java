package com.emrehalli.financeportal.market.domain;

import java.time.Duration;

public record FreshnessPolicy(Duration maxAge) {

    public static FreshnessPolicy ofMinutes(long minutes) {
        return new FreshnessPolicy(Duration.ofMinutes(minutes));
    }

    public boolean isFresh(java.time.Instant lastUpdatedAt, java.time.Clock clock) {
        if (lastUpdatedAt == null) {
            return false;
        }
        return lastUpdatedAt.plus(maxAge).isAfter(clock.instant());
    }
}