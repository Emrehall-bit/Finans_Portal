package com.emrehalli.financeportal.market.support;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class SymbolNormalizer {

    public Optional<String> normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        String normalized = symbol.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }
}
