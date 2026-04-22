package com.emrehalli.financeportal.market.enums;

import java.time.Duration;
import java.time.LocalDateTime;

public enum MarketDataFreshness {
    REALTIME,
    DELAYED,
    STALE,
    UNKNOWN;

    public static MarketDataFreshness from(LocalDateTime priceTime, LocalDateTime fetchedAt) {
        LocalDateTime referenceTime = priceTime != null ? priceTime : fetchedAt;

        if (referenceTime == null) {
            return UNKNOWN;
        }

        Duration age = Duration.between(referenceTime, LocalDateTime.now());

        if (age.isNegative() || age.toMinutes() <= 15) {
            return REALTIME;
        }

        if (age.toHours() <= 24) {
            return DELAYED;
        }

        return STALE;
    }
}
