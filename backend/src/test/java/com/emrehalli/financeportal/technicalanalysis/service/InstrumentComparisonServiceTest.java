package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentComparisonServiceTest {

    private HistoricalPriceReader historicalPriceReader;
    private InstrumentComparisonService service;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 5);

    @BeforeEach
    void setUp() {
        historicalPriceReader = mock(HistoricalPriceReader.class);
        service = new InstrumentComparisonService(historicalPriceReader);
    }

    @Test
    void comparison_normalizes_each_series_to_100_at_start() {
        when(historicalPriceReader.read("THYAO", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("THYAO", FROM, new BigDecimal("200.00")),
                new HistoricalPricePoint("THYAO", FROM.plusDays(1), new BigDecimal("220.00")),
                new HistoricalPricePoint("THYAO", FROM.plusDays(2), new BigDecimal("180.00"))
        ));
        when(historicalPriceReader.read("GARAN", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("GARAN", FROM, new BigDecimal("50.00")),
                new HistoricalPricePoint("GARAN", FROM.plusDays(1), new BigDecimal("55.00"))
        ));

        ComparisonResponse result = service.compare(List.of("THYAO", "GARAN"), FROM, TO);

        assertThat(result.series()).hasSize(2);

        ComparisonResponse.Series thyaoSeries = result.series().stream()
                .filter(s -> "THYAO".equals(s.symbol())).findFirst().orElseThrow();
        // First point normalized value must be 100
        assertThat(thyaoSeries.points().getFirst().normalizedValue()).isEqualByComparingTo("100");
        // Second point: 220/200 * 100 = 110
        assertThat(thyaoSeries.points().get(1).normalizedValue()).isEqualByComparingTo("110");
        // Third point: 180/200 * 100 = 90
        assertThat(thyaoSeries.points().get(2).normalizedValue()).isEqualByComparingTo("90");

        ComparisonResponse.Series garanSeries = result.series().stream()
                .filter(s -> "GARAN".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(garanSeries.points().getFirst().normalizedValue()).isEqualByComparingTo("100");
        assertThat(garanSeries.points().get(1).normalizedValue()).isEqualByComparingTo("110");
    }

    @Test
    void comparison_throws_when_symbol_list_is_empty() {
        assertThatThrownBy(() -> service.compare(List.of(), FROM, TO))
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("2 symbols");
    }

    @Test
    void comparison_throws_when_no_history_found_for_a_symbol() {
        when(historicalPriceReader.read("THYAO", FROM, TO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.compare(List.of("THYAO", "GARAN"), FROM, TO))
                .isInstanceOf(TechnicalAnalysisException.NotFound.class)
                .hasMessageContaining("THYAO");
    }

    @Test
    void comparison_throws_when_first_price_is_zero_preventing_normalization() {
        when(historicalPriceReader.read("THYAO", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("THYAO", FROM, BigDecimal.ZERO)
        ));

        assertThatThrownBy(() -> service.compare(List.of("THYAO", "GARAN"), FROM, TO))
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("THYAO");
    }

    @Test
    void comparison_handles_multiple_symbols_independently() {
        when(historicalPriceReader.read("THYAO", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("THYAO", FROM, new BigDecimal("100.00")),
                new HistoricalPricePoint("THYAO", FROM.plusDays(1), new BigDecimal("150.00"))
        ));
        when(historicalPriceReader.read("GARAN", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("GARAN", FROM, new BigDecimal("40.00")),
                new HistoricalPricePoint("GARAN", FROM.plusDays(1), new BigDecimal("30.00"))
        ));
        when(historicalPriceReader.read("AKBNK", FROM, TO)).thenReturn(List.of(
                new HistoricalPricePoint("AKBNK", FROM, new BigDecimal("60.00")),
                new HistoricalPricePoint("AKBNK", FROM.plusDays(1), new BigDecimal("60.00"))
        ));

        ComparisonResponse result = service.compare(List.of("THYAO", "GARAN", "AKBNK"), FROM, TO);

        assertThat(result.series()).hasSize(3);

        // THYAO: +50% → normalized second point = 150
        ComparisonResponse.Series thyao = result.series().stream().filter(s -> "THYAO".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(thyao.points().get(1).normalizedValue()).isEqualByComparingTo("150");

        // GARAN: -25% → normalized second point = 75
        ComparisonResponse.Series garan = result.series().stream().filter(s -> "GARAN".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(garan.points().get(1).normalizedValue()).isEqualByComparingTo("75");

        // AKBNK: flat → normalized second point = 100
        ComparisonResponse.Series akbnk = result.series().stream().filter(s -> "AKBNK".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(akbnk.points().get(1).normalizedValue()).isEqualByComparingTo("100");
    }
}
