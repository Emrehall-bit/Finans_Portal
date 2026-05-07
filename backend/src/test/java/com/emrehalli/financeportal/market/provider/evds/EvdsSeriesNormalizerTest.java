package com.emrehalli.financeportal.market.provider.evds;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsSeriesNormalizerTest {

    private final EvdsSeriesNormalizer normalizer = new EvdsSeriesNormalizer();

    @Test
    void preservesTrailingNumericSuffix() {
        assertThat(normalizer.normalizeRequestCode("TP.TUKFIY2025.GENEL-1")).contains("TP.TUKFIY2025.GENEL-1");
        assertThat(normalizer.normalizeRequestCode("TP.TIG08-1")).contains("TP.TIG08-1");
        assertThat(normalizer.normalizeRequestCode("TP.TRY.MT06")).contains("TP.TRY.MT06");
    }

    @Test
    void normalizesUnderscoresToDots() {
        assertThat(normalizer.normalizeRequestCode("tp_tig08_1")).contains("TP.TIG08.1");
    }
}
