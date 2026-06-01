package com.emrehalli.financeportal.technicalanalysis.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RsiServiceTest {

    private final RsiService service = new RsiService();

    @Test
    void calculateRsi_should_return_all_null_when_data_points_not_exceed_period() {
        // values.size() <= period → no RSI value can be seeded; entire list is null.
        List<BigDecimal> exactly14 = price(100, 14);
        List<BigDecimal> result = service.calculateRsi(exactly14, 14);

        assertThat(result).hasSize(14);
        assertThat(result).allMatch(v -> v == null);
    }

    @Test
    void calculateRsi_edge_cases_flat_allUp_allDown_produce_50_100_0() {
        // Flat series: no gains, no losses → both averages 0 → RSI = 50 (neutral sentinel).
        List<BigDecimal> flat = price(100, 15);
        assertThat(service.calculateRsi(flat, 14).get(14)).isEqualByComparingTo("50");

        // Always rising: all gains, no losses → averageLoss = 0 → RSI = 100.
        List<BigDecimal> rising = IntStream.rangeClosed(100, 114)
                .mapToObj(i -> BigDecimal.valueOf(i))
                .toList();
        assertThat(service.calculateRsi(rising, 14).get(14)).isEqualByComparingTo("100");

        // Always falling: no gains, all losses → averageGain = 0 → RSI = 0.
        List<BigDecimal> falling = IntStream.iterate(114, i -> i - 1)
                .limit(15)
                .mapToObj(i -> BigDecimal.valueOf(i))
                .toList();
        assertThat(service.calculateRsi(falling, 14).get(14)).isEqualByComparingTo("0");
    }

    @Test
    void calculateRsi_wilder_smoothing_produces_expected_value_for_alternating_series() {
        // Series: 10,12,11,13,...,18,17 — alternates +2/-1 over 14 changes.
        // Period-14 seed: avgGain = 14/14 = 1.0, avgLoss = 7/14 = 0.5
        // RS = 2.0 → RSI = 100 − 100/3 ≈ 66.67
        List<BigDecimal> prices = List.of(
                bd(10), bd(12), bd(11), bd(13), bd(12), bd(14), bd(13),
                bd(15), bd(14), bd(16), bd(15), bd(17), bd(16), bd(18), bd(17)
        );
        BigDecimal rsi = service.calculateRsi(prices, 14).get(14);

        assertThat(rsi).isBetween(BigDecimal.valueOf(66), BigDecimal.valueOf(68));
    }

    private static List<BigDecimal> price(double value, int count) {
        return Collections.nCopies(count, BigDecimal.valueOf(value));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
