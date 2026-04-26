package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentComparisonServiceTest {

    @Test
    void firstValueIsNormalizedToOneHundred() {
        InstrumentComparisonService service = new InstrumentComparisonService((symbol, from, to) -> List.of(
                point(symbol, "2026-04-25", "10"),
                point(symbol, "2026-04-26", "12")
        ));

        ComparisonResult result = service.compare(List.of("USDTRY", "EURTRY"), date("2026-04-25"), date("2026-04-26"));

        assertThat(result.series()).hasSize(2);
        assertThat(result.series().getFirst().points().getFirst().normalizedValue()).isEqualByComparingTo("100");
    }

    @Test
    void subsequentValueUsesCloseDividedByFirstCloseTimesOneHundred() {
        InstrumentComparisonService service = new InstrumentComparisonService((symbol, from, to) -> List.of(
                point(symbol, "2026-04-25", "10"),
                point(symbol, "2026-04-26", "12.5")
        ));

        ComparisonResult result = service.compare(List.of("USDTRY", "EURTRY"), date("2026-04-25"), date("2026-04-26"));

        assertThat(result.series().getFirst().points().get(1).normalizedValue()).isEqualByComparingTo("125.00000000");
    }

    @Test
    void emptyHistoryIsHandledSafely() {
        InstrumentComparisonService service = new InstrumentComparisonService((symbol, from, to) -> List.of());

        assertThatThrownBy(() -> service.compare(List.of("USDTRY", "EURTRY"), date("2026-04-25"), date("2026-04-26")))
                .isInstanceOf(TechnicalAnalysisNotFoundException.class)
                .hasMessageContaining("Historical price data not found");
    }

    private static HistoricalPricePoint point(String symbol, String date, String close) {
        return new HistoricalPricePoint(symbol, LocalDate.parse(date), new BigDecimal(close));
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}
