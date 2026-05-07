package com.emrehalli.financeportal.market.provider.evds;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsSeriesValidatorTest {

    private final EvdsSeriesValidator validator = new EvdsSeriesValidator(new EvdsSeriesNormalizer());

    @Test
    void rejectsNullBlankAndIllegalCodes() {
        assertThat(validator.validate(null).valid()).isFalse();
        assertThat(validator.validate("   ").valid()).isFalse();
        assertThat(validator.validate("TP.TIG08$").valid()).isFalse();
    }

    @Test
    void acceptsNormalizedValidCode() {
        EvdsSeriesValidator.ValidationResult result = validator.validate("TP.TIG08-1");

        assertThat(result.valid()).isTrue();
        assertThat(result.normalizedCode()).isEqualTo("TP.TIG08-1");
    }
}
