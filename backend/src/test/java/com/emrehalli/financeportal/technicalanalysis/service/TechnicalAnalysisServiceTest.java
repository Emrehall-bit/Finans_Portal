package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisValidationException;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void analyze_should_reject_symbol_with_invalid_characters() {
        TechnicalAnalysisService service = buildService(mock(HistoricalPriceReader.class));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);

        assertThatThrownBy(() -> service.analyze("BTC<USDT>", from, to, null))
                .isInstanceOf(TechnicalAnalysisValidationException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void analyze_should_reject_symbol_exceeding_max_length() {
        TechnicalAnalysisService service = buildService(mock(HistoricalPriceReader.class));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        String tooLong = "A".repeat(31);

        assertThatThrownBy(() -> service.analyze(tooLong, from, to, null))
                .isInstanceOf(TechnicalAnalysisValidationException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void analyze_should_accept_valid_symbol_formats() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        when(historicalPriceReader.read(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        TechnicalAnalysisService service = buildService(historicalPriceReader);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);

        assertThat(service.analyze("THYAO", from, to, null).symbol()).isEqualTo("THYAO");
        assertThat(service.analyze("TCMB:USD:SELL", from, to, null).symbol()).isEqualTo("TCMB:USD:SELL");
        assertThat(service.analyze("BTCUSDT", from, to, null).symbol()).isEqualTo("BTCUSDT");
        assertThat(service.analyze("USDTRY", from, to, null).symbol()).isEqualTo("USDTRY");
    }

    private static TechnicalAnalysisService buildService(HistoricalPriceReader historicalPriceReader) {
        AppMessageSource appMessageSource = mock(AppMessageSource.class);
        return new TechnicalAnalysisService(
                historicalPriceReader,
                new MovingAverageService(),
                new RsiService(),
                new TrendAnalysisService(),
                mock(InstrumentComparisonService.class),
                appMessageSource
        );
    }
}

