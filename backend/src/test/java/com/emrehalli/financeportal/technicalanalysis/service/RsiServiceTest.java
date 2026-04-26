package com.emrehalli.financeportal.technicalanalysis.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RsiServiceTest {

    private final RsiService rsiService = new RsiService();

    @Test
    void risingSeriesProducesHighRsi() {
        List<BigDecimal> result = rsiService.calculateRsi(
                List.of(
                        bd("10"), bd("11"), bd("12"), bd("13"), bd("14"),
                        bd("15"), bd("16"), bd("17"), bd("18"), bd("19"),
                        bd("20"), bd("21"), bd("22"), bd("23"), bd("24")
                ),
                14
        );

        assertThat(result).hasSize(15);
        assertThat(result.get(14)).isEqualByComparingTo("100");
    }

    @Test
    void fallingSeriesProducesLowRsi() {
        List<BigDecimal> result = rsiService.calculateRsi(
                List.of(
                        bd("24"), bd("23"), bd("22"), bd("21"), bd("20"),
                        bd("19"), bd("18"), bd("17"), bd("16"), bd("15"),
                        bd("14"), bd("13"), bd("12"), bd("11"), bd("10")
                ),
                14
        );

        assertThat(result.get(14)).isEqualByComparingTo("0");
    }

    @Test
    void flatSeriesBehavesSafely() {
        List<BigDecimal> result = rsiService.calculateRsi(
                List.of(
                        bd("10"), bd("10"), bd("10"), bd("10"), bd("10"),
                        bd("10"), bd("10"), bd("10"), bd("10"), bd("10"),
                        bd("10"), bd("10"), bd("10"), bd("10"), bd("10")
                ),
                14
        );

        assertThat(result.get(14)).isEqualByComparingTo("50");
    }

    @Test
    void returnsNullWhenThereIsNotEnoughData() {
        List<BigDecimal> result = rsiService.calculateRsi(
                List.of(
                        bd("10"), bd("11"), bd("12"), bd("13"), bd("14"),
                        bd("15"), bd("16"), bd("17"), bd("18"), bd("19"),
                        bd("20"), bd("21"), bd("22"), bd("23")
                ),
                14
        );

        assertThat(result).hasSize(14);
        assertThat(result).containsOnlyNulls();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
