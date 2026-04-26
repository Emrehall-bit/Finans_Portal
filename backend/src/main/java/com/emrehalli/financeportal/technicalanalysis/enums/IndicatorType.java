package com.emrehalli.financeportal.technicalanalysis.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum IndicatorType {
    SMA7(7),
    SMA20(20),
    SMA50(50),
    RSI14(14);

    private final int period;

    IndicatorType(int period) {
        this.period = period;
    }

    public int period() {
        return period;
    }

    public static Set<IndicatorType> defaultIndicators() {
        return Arrays.stream(values())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Optional<IndicatorType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(indicatorType -> indicatorType.name().equals(normalized))
                .findFirst();
    }
}
