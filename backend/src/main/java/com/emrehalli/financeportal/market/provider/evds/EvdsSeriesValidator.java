package com.emrehalli.financeportal.market.provider.evds;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class EvdsSeriesValidator {

    private static final Pattern LEGAL_CODE_PATTERN = Pattern.compile("^[A-Z0-9._-]+$");

    private final EvdsSeriesNormalizer normalizer;

    public EvdsSeriesValidator(EvdsSeriesNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public ValidationResult validate(String providerSymbol) {
        if (providerSymbol == null || providerSymbol.isBlank()) {
            return ValidationResult.invalid(providerSymbol, "blank");
        }

        Optional<String> normalized = normalizer.normalizeRequestCode(providerSymbol);
        if (normalized.isEmpty() || normalized.get().isBlank()) {
            return ValidationResult.invalid(providerSymbol, "blank_after_normalization");
        }

        if (!LEGAL_CODE_PATTERN.matcher(normalized.get()).matches()) {
            return ValidationResult.invalid(providerSymbol, "illegal_characters");
        }

        return ValidationResult.valid(providerSymbol.trim(), normalized.get());
    }

    public record ValidationResult(
            String originalCode,
            String normalizedCode,
            boolean valid,
            String reason
    ) {
        private static ValidationResult valid(String originalCode, String normalizedCode) {
            return new ValidationResult(originalCode, normalizedCode, true, null);
        }

        private static ValidationResult invalid(String originalCode, String reason) {
            return new ValidationResult(originalCode, null, false, reason);
        }
    }
}
