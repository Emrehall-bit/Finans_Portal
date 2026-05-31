package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
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
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
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
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void analyze_should_reject_symbol_exceeding_max_length() {
        TechnicalAnalysisService service = buildService(mock(HistoricalPriceReader.class));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        String tooLong = "A".repeat(31);

        assertThatThrownBy(() -> service.analyze(tooLong, from, to, null))
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("too long");
    }

    @Test
    void analyze_should_accept_valid_symbol_formats() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        when(historicalPriceReader.read(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> validHistory(invocation.getArgument(0)));

        TechnicalAnalysisService service = buildService(historicalPriceReader);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);

        assertThat(service.analyze("THYAO", from, to, null).symbol()).isEqualTo("THYAO");
        assertThat(service.analyze("TCMB:USD:SELL", from, to, null).symbol()).isEqualTo("TCMB:USD:SELL");
        assertThat(service.analyze("BTCUSDT", from, to, null).symbol()).isEqualTo("BTCUSDT");
        assertThat(service.analyze("USDTRY", from, to, null).symbol()).isEqualTo("USDTRY");
    }

    @Test
    void analyze_should_not_compute_unselected_indicators() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        LocalDate start = LocalDate.of(2026, 1, 1);
        when(historicalPriceReader.read("BTCUSDT", start, start.plusDays(60)))
                .thenReturn(increasingHistory(start, 61));

        TechnicalAnalysisService service = buildService(historicalPriceReader);

        TechnicalAnalysisResult result = service.analyze("BTCUSDT", start, start.plusDays(60), "SMA7");

        // Yalnızca SMA7 (ve trend/sinyal için zorunlu SMA20) hesaplanır; SMA50 ve RSI14 hiç hesaplanmaz.
        assertThat(result.points().getLast().sma7()).isNotNull();
        assertThat(result.points()).allMatch(point -> point.sma50() == null);
        assertThat(result.points()).allMatch(point -> point.rsi14() == null);
        assertThat(result.indicatorValues()).containsKey(IndicatorType.SMA7);
        assertThat(result.indicatorValues()).doesNotContainKey(IndicatorType.SMA50);
        assertThat(result.indicatorValues()).doesNotContainKey(IndicatorType.RSI14);
    }

    @Test
    void analyze_blank_indicators_should_compute_all_four_indicators() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        LocalDate start = LocalDate.of(2026, 1, 1);
        when(historicalPriceReader.read("BTCUSDT", start, start.plusDays(60)))
                .thenReturn(increasingHistory(start, 61));

        TechnicalAnalysisService service = buildService(historicalPriceReader);

        TechnicalAnalysisResult result = service.analyze("BTCUSDT", start, start.plusDays(60), null);

        TechnicalAnalysisResult.Point last = result.points().getLast();
        assertThat(last.sma7()).isNotNull();
        assertThat(last.sma20()).isNotNull();
        assertThat(last.sma50()).isNotNull();
        assertThat(last.rsi14()).isNotNull();
    }

    @Test
    void analyze_should_compute_available_indicators_without_global_sixty_point_threshold() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        LocalDate start = LocalDate.of(2026, 1, 1);
        when(historicalPriceReader.read("BTCUSDT", start, start.plusDays(14)))
                .thenReturn(increasingHistory(start, 15));

        TechnicalAnalysisService service = buildService(historicalPriceReader);

        TechnicalAnalysisResult result = service.analyze("BTCUSDT", start, start.plusDays(14), "SMA7,SMA20,SMA50,RSI14");
        TechnicalAnalysisResult.Point last = result.points().getLast();

        assertThat(result.analysisStatus()).isEqualTo("AVAILABLE");
        assertThat(last.sma7()).isNotNull();
        assertThat(last.rsi14()).isNotNull();
        assertThat(last.sma20()).isNull();
        assertThat(last.sma50()).isNull();
        assertThat(result.indicatorValues()).containsKey(IndicatorType.SMA7);
        assertThat(result.indicatorValues()).containsKey(IndicatorType.RSI14);
        assertThat(result.indicatorValues()).doesNotContainKey(IndicatorType.SMA20);
        assertThat(result.indicatorValues()).doesNotContainKey(IndicatorType.SMA50);
    }

    @Test
    void analyze_should_compute_sma50_when_fifty_or_more_points_are_available() {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        LocalDate start = LocalDate.of(2026, 3, 3);
        when(historicalPriceReader.read("TCMB:AUD:SELL", start, start.plusDays(90)))
                .thenReturn(increasingHistory("TCMB:AUD:SELL", start, 58));

        TechnicalAnalysisService service = buildService(historicalPriceReader);

        TechnicalAnalysisResult result = service.analyze("TCMB:AUD:SELL", start, start.plusDays(90), "SMA7,SMA20,SMA50,RSI14");
        TechnicalAnalysisResult.Point last = result.points().getLast();

        assertThat(result.analysisStatus()).isEqualTo("AVAILABLE");
        assertThat(result.points()).hasSize(58);
        assertThat(last.sma7()).isNotNull();
        assertThat(last.sma20()).isNotNull();
        assertThat(last.sma50()).isNotNull();
        assertThat(last.rsi14()).isNotNull();
    }

    @Test
    void analyze_should_handle_representative_indicator_scenarios() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        TechnicalAnalysisResult rising = analyzeHistory("RISING", increasingHistory("RISING", start, 61));
        TechnicalAnalysisResult.Point risingLast = rising.points().getLast();
        assertThat(risingLast.sma7()).isNotNull();
        assertThat(risingLast.rsi14()).isBetween(BigDecimal.valueOf(99), BigDecimal.valueOf(100));

        TechnicalAnalysisResult falling = analyzeHistory("FALLING", decreasingHistory("FALLING", start, 61));
        TechnicalAnalysisResult.Point fallingLast = falling.points().getLast();
        assertThat(fallingLast.rsi14()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(falling.trendDirection()).isEqualTo(TrendDirection.DOWNTREND);
        assertThat(falling.signals()).contains(TechnicalSignal.PRICE_BELOW_SMA20, TechnicalSignal.SMA7_BELOW_SMA20);

        TechnicalAnalysisResult flat = analyzeHistory("FLAT", flatHistory("FLAT", start, 61));
        assertThat(flat.points().getLast().rsi14()).isBetween(BigDecimal.valueOf(45), BigDecimal.valueOf(55));

        TechnicalAnalysisResult shortHistory = analyzeHistory("SHORT", increasingHistory("SHORT", start, 6));
        TechnicalAnalysisResult.Point shortLast = shortHistory.points().getLast();
        assertThat(shortLast.sma7()).isNull();
        assertThat(shortLast.rsi14()).isNull();

        TechnicalAnalysisResult medium = analyzeHistory("MEDIUM", increasingHistory("MEDIUM", start, 15));
        TechnicalAnalysisResult.Point mediumLast = medium.points().getLast();
        assertThat(mediumLast.sma7()).isNotNull();
        assertThat(mediumLast.rsi14()).isNotNull();
        assertThat(mediumLast.sma20()).isNull();
        assertThat(mediumLast.sma50()).isNull();

        TechnicalAnalysisResult enough = analyzeHistory("ENOUGH", increasingHistory("ENOUGH", start, 58));
        TechnicalAnalysisResult.Point enoughLast = enough.points().getLast();
        assertThat(enoughLast.sma7()).isNotNull();
        assertThat(enoughLast.sma20()).isNotNull();
        assertThat(enoughLast.sma50()).isNotNull();
        assertThat(enoughLast.rsi14()).isNotNull();
    }

    private static List<HistoricalPricePoint> increasingHistory(LocalDate start, int count) {
        return increasingHistory("BTCUSDT", start, count);
    }

    private static List<HistoricalPricePoint> increasingHistory(String symbol, LocalDate start, int count) {
        List<HistoricalPricePoint> history = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            history.add(new HistoricalPricePoint(symbol, start.plusDays(index), BigDecimal.valueOf(100 + index)));
        }
        return history;
    }

    private static List<HistoricalPricePoint> decreasingHistory(String symbol, LocalDate start, int count) {
        List<HistoricalPricePoint> history = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            history.add(new HistoricalPricePoint(symbol, start.plusDays(index), BigDecimal.valueOf(200 - index)));
        }
        return history;
    }

    private static List<HistoricalPricePoint> flatHistory(String symbol, LocalDate start, int count) {
        List<HistoricalPricePoint> history = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            history.add(new HistoricalPricePoint(symbol, start.plusDays(index), BigDecimal.valueOf(100)));
        }
        return history;
    }

    private static TechnicalAnalysisResult analyzeHistory(String symbol, List<HistoricalPricePoint> history) {
        HistoricalPriceReader historicalPriceReader = mock(HistoricalPriceReader.class);
        LocalDate from = history.getFirst().date();
        LocalDate to = history.getLast().date();
        when(historicalPriceReader.read(symbol, from, to)).thenReturn(history);
        return buildService(historicalPriceReader).analyze(symbol, from, to, "SMA7,SMA20,SMA50,RSI14");
    }

    private static TechnicalAnalysisService buildService(HistoricalPriceReader historicalPriceReader) {
        AppMessageSource appMessageSource = mock(AppMessageSource.class);
        return new TechnicalAnalysisService(
                historicalPriceReader,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                new TrendAnalysisService(),
                mock(InstrumentComparisonService.class),
                appMessageSource
        );
    }

    private static List<HistoricalPricePoint> validHistory(String symbol) {
        return List.of(new HistoricalPricePoint(
                symbol,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(100)
        ));
    }
}
