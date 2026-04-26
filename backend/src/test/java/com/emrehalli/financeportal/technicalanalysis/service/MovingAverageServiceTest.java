package com.emrehalli.financeportal.technicalanalysis.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovingAverageServiceTest {

    private final MovingAverageService movingAverageService = new MovingAverageService();

    @Test
    void calculatesSma7WhenEnoughDataExists() {
        List<BigDecimal> result = movingAverageService.calculateSimpleMovingAverage(
                List.of(
                        new BigDecimal("10"),
                        new BigDecimal("20"),
                        new BigDecimal("30"),
                        new BigDecimal("40"),
                        new BigDecimal("50"),
                        new BigDecimal("60"),
                        new BigDecimal("70")
                ),
                7
        );

        assertThat(result).hasSize(7);
        assertThat(result.subList(0, 6)).containsOnlyNulls();
        assertThat(result.get(6)).isEqualByComparingTo("40.00000000");
    }

    @Test
    void returnsNullPlaceholdersWhenThereAreOnlySixValuesForSma7() {
        List<BigDecimal> result = movingAverageService.calculateSimpleMovingAverage(
                List.of(
                        new BigDecimal("10"),
                        new BigDecimal("20"),
                        new BigDecimal("30"),
                        new BigDecimal("40"),
                        new BigDecimal("50"),
                        new BigDecimal("60")
                ),
                7
        );

        assertThat(result).hasSize(6);
        assertThat(result).containsOnlyNulls();
    }

    @Test
    void returnsEmptyListForEmptyInputWithoutFailing() {
        List<BigDecimal> result = movingAverageService.calculateSimpleMovingAverage(List.of(), 7);

        assertThat(result).isEmpty();
    }
}
