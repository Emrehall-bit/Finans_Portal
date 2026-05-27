package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TechnicalAnalysisServiceTest {

    @Test
    void analyze_should_skip_null_close_points_before_indicator_calculation() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        InstrumentComparisonService instrumentComparisonService = mock(InstrumentComparisonService.class);
        AppMessageSource appMessageSource = mock(AppMessageSource.class);

        List<HistoricalPricePoint> history = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < 61; index++) {
            history.add(new HistoricalPricePoint(
                    "BTCUSDT",
                    start.plusDays(index),
                    index == 10 ? null : BigDecimal.valueOf(100 + index)
            ));
        }

        when(historicalPriceReader.read("BTCUSDT", start, start.plusDays(60))).thenReturn(history);

        TechnicalAnalysisService service = new TechnicalAnalysisService(
                historicalPriceReader,
                new MovingAverageService(),
                new RsiService(),
                new TrendAnalysisService(),
                instrumentComparisonService,
                appMessageSource
        );

        TechnicalAnalysisResult result = service.analyze("BTCUSDT", start, start.plusDays(60), "SMA7,SMA20,SMA50,RSI14");

        assertThat(result.analysisStatus()).isEqualTo("AVAILABLE");
        assertThat(result.points()).hasSize(60);
        assertThat(result.points()).allMatch(point -> point.close() != null);
        assertThat(result.latestPrice()).isEqualByComparingTo("160");
    }
}
