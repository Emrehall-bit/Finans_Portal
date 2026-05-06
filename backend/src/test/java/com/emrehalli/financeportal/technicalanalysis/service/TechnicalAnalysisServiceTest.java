package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillProperties;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicalAnalysisServiceTest {

    @Mock
    private HistoricalPriceReader historicalPriceReader;

    @Mock
    private MovingAverageService movingAverageService;

    @Mock
    private RsiService rsiService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @Mock
    private InstrumentComparisonService instrumentComparisonService;

    @Mock
    private AppMessageSource appMessageSource;

    @Test
    void analyzeOmitsNullIndicatorValuesInsteadOfThrowing() {
        TechnicalAnalysisService service = new TechnicalAnalysisService(
                historicalPriceReader,
                movingAverageService,
                rsiService,
                trendAnalysisService,
                instrumentComparisonService,
                historyProperties(),
                appMessageSource
        );

        when(historicalPriceReader.read(eq("TCD"), any(), any())).thenReturn(List.of(
                new HistoricalPricePoint("TCD", LocalDate.of(2026, 4, 23), new BigDecimal("12.10")),
                new HistoricalPricePoint("TCD", LocalDate.of(2026, 4, 24), new BigDecimal("12.30"))
        ));

        var result = service.analyze(
                "TCD",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 24),
                "SMA7,SMA20,SMA50,RSI14"
        );

        assertThat(result.symbol()).isEqualTo("TCD");
        assertThat(result.analysisStatus()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(result.points()).isEmpty();
    }

    @Test
    void analyzeReturnsInsufficientHistoryInsteadOfThrowing() {
        TechnicalAnalysisService service = new TechnicalAnalysisService(
                historicalPriceReader,
                movingAverageService,
                rsiService,
                trendAnalysisService,
                instrumentComparisonService,
                historyProperties(),
                appMessageSource
        );

        when(historicalPriceReader.read(eq("TCD"), any(), any())).thenReturn(List.of(
                new HistoricalPricePoint("TCD", LocalDate.of(2026, 4, 24), new BigDecimal("12.30"))
        ));

        var result = service.analyze("TCD", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 24), "SMA7,SMA20");

        assertThat(result.analysisStatus()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(result.indicatorValues()).isEqualTo(Map.of());
        assertThat(result.points()).isEmpty();
    }

    private MarketHistoryBackfillProperties historyProperties() {
        MarketHistoryBackfillProperties properties = new MarketHistoryBackfillProperties();
        properties.setRequiredHistoryPointCount(50);
        return properties;
    }
}
