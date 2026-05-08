package com.emrehalli.financeportal.market.provider.evds;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class EvdsSeriesNormalizer {

    private static final String FOREX_SERIES_PREFIX = "TP_DK_";

    public Optional<String> normalizeRequestCode(String providerSymbol) {
        if (providerSymbol == null || providerSymbol.isBlank()) {
            return Optional.empty();
        }

        String normalized = providerSymbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(FOREX_SERIES_PREFIX)) {
            normalized = normalized.replace('_', '.');
        }

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }
}
