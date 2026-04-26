package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisServiceTest {

    private final TrendAnalysisService trendAnalysisService = new TrendAnalysisService();

    @Test
    void returnsUptrendWhenPriceAndSma7AreAboveSma20AndPriceIsRising() {
        TrendDirection trend = trendAnalysisService.determineTrend(
                point("2026-04-26", "100", null, null, null, null),
                point("2026-04-27", "110", "108", "105", null, "72")
        );

        assertThat(trend).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void returnsDowntrendWhenPriceAndSma7AreBelowSma20AndPriceIsFalling() {
        TrendDirection trend = trendAnalysisService.determineTrend(
                point("2026-04-26", "110", null, null, null, null),
                point("2026-04-27", "100", "101", "105", null, "22")
        );

        assertThat(trend).isEqualTo(TrendDirection.DOWNTREND);
    }

    @Test
    void returnsSidewaysWhenThereIsNotEnoughData() {
        TrendDirection trend = trendAnalysisService.determineTrend(
                point("2026-04-26", "100", null, null, null, null),
                point("2026-04-27", "110", null, "105", null, null)
        );

        assertThat(trend).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void derivesRsiSignals() {
        assertThat(trendAnalysisService.determineSignals(point("2026-04-27", "110", "108", "105", null, "75")))
                .contains(TechnicalSignal.RSI_OVERBOUGHT);

        assertThat(trendAnalysisService.determineSignals(point("2026-04-27", "90", "92", "95", null, "25")))
                .contains(TechnicalSignal.RSI_OVERSOLD);

        assertThat(trendAnalysisService.determineSignals(point("2026-04-27", "100", "100", "100", null, "50")))
                .contains(TechnicalSignal.RSI_NEUTRAL);
    }

    private static TechnicalAnalysisPoint point(String date,
                                                String close,
                                                String sma7,
                                                String sma20,
                                                String sma50,
                                                String rsi14) {
        return new TechnicalAnalysisPoint(
                LocalDate.parse(date),
                bd(close),
                bd(sma7),
                bd(sma20),
                bd(sma50),
                bd(rsi14)
        );
    }

    private static BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
