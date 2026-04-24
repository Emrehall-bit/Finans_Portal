package com.emrehalli.financeportal.market.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolNormalizerTest {

    private final SymbolNormalizer symbolNormalizer = new SymbolNormalizer();

    @Test
    void normalizeReturnsCanonicalSymbolForCommonFxVariations() {
        assertThat(symbolNormalizer.normalize("usdtry")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize("USDTRY")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize("USD/TRY")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize("usd/try")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize(" usd / try ")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize("usd-try")).contains("USDTRY");
        assertThat(symbolNormalizer.normalize("usd_try")).contains("USDTRY");
    }

    @Test
    void normalizeReturnsEmptyForBlankInput() {
        assertThat(symbolNormalizer.normalize(null)).isEmpty();
        assertThat(symbolNormalizer.normalize("")).isEmpty();
        assertThat(symbolNormalizer.normalize("   ")).isEmpty();
    }
}
