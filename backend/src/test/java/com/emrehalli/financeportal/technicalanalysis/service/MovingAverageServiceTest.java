package com.emrehalli.financeportal.technicalanalysis.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovingAverageServiceTest {

    private final MovingAverageService service = new MovingAverageService();

    @Test
    void calculateSimpleMovingAverage_should_return_null_during_warmup_period() {
        List<BigDecimal> result = service.calculateSimpleMovingAverage(
                List.of(
                        BigDecimal.ONE,
                        BigDecimal.TWO,
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(4)
                ),
                3
        );

        assertThat(result).containsExactly(
                null,
                null,
                BigDecimal.valueOf(2).setScale(8),
                BigDecimal.valueOf(3).setScale(8)
        );
    }

    @Test
    void calculateSimpleMovingAverage_should_keep_null_when_window_contains_missing_value() {
        List<BigDecimal> result = service.calculateSimpleMovingAverage(
                Arrays.asList(
                        BigDecimal.ONE,
                        null,
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(4),
                        BigDecimal.valueOf(5)
                ),
                3
        );

        assertThat(result).containsExactly(
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(4).setScale(8)
        );
    }
}
