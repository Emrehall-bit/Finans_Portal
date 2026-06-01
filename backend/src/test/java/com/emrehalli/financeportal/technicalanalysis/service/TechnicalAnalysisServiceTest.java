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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        // Service now calls read with warmupFrom (from - 90 days), use any() for date args
        when(historicalPriceReader.read(eq("BTCUSDT"), any(), any())).thenReturn(history);

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
        when(historicalPriceReader.read(eq("BTCUSDT"), any(), any()))
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
        when(historicalPriceReader.read(eq("BTCUSDT"), any(), any()))
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
        when(historicalPriceReader.read(eq("BTCUSDT"), any(), any()))
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
        when(historicalPriceReader.read(eq("TCMB:AUD:SELL"), any(), any()))
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

    // --- Warmup testleri ---

    @Test
    void analyze_should_provide_stable_rsi_at_first_visible_day_when_warmup_data_is_available() {
        // Senaryo: kullanıcı 30 günlük aralık (from..to) istiyor.
        // Mock, from - WARMUP_DAYS tarihinden itibaren 120 günlük sürekli artan fiyat veriyor.
        // Beklenti: from'daki ilk görünür noktada RSI14 ve SMA50 zaten hesaplanmış olmalı
        // (warm-up verisi sayesinde).
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(29);
        LocalDate warmupStart = from.minusDays(TechnicalAnalysisService.INDICATOR_WARMUP_DAYS);

        // Toplam 120 gün: 90 gün warmup + 30 gün görünür
        List<HistoricalPricePoint> fullData = increasingHistory("USDTRY", warmupStart, 120);

        HistoricalPriceReader reader = mock(HistoricalPriceReader.class);
        when(reader.read(eq("USDTRY"), any(), any())).thenReturn(fullData);

        TechnicalAnalysisResult result = buildService(reader).analyze("USDTRY", from, to, "SMA7,SMA20,SMA50,RSI14");

        // Yalnızca görünür aralık döner
        assertThat(result.points()).hasSize(30);
        assertThat(result.points()).allMatch(point -> !point.date().isBefore(from));

        // İlk görünür noktada (from) warm-up sayesinde RSI ve SMA'lar hesaplanmış olmalı
        TechnicalAnalysisResult.Point firstVisible = result.points().getFirst();
        assertThat(firstVisible.date()).isEqualTo(from);
        assertThat(firstVisible.rsi14())
                .as("RSI14 should be non-null at first visible day thanks to warmup")
                .isNotNull();
        assertThat(firstVisible.sma20())
                .as("SMA20 should be non-null at first visible day thanks to warmup")
                .isNotNull();
        assertThat(firstVisible.sma50())
                .as("SMA50 should be non-null at first visible day thanks to warmup")
                .isNotNull();
    }

    @Test
    void analyze_should_trim_response_points_to_visible_from_to_range() {
        // Senaryo: mock warmup + görünür veriyi birlikte döndürüyor.
        // Beklenti: response.points() yalnızca from..to aralığını içermeli; warmup günleri olmamalı.
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = from.plusDays(19);
        LocalDate warmupStart = from.minusDays(TechnicalAnalysisService.INDICATOR_WARMUP_DAYS);

        // 110 gün toplam (90 warmup + 20 görünür)
        List<HistoricalPricePoint> fullData = increasingHistory("THYAO", warmupStart, 110);

        HistoricalPriceReader reader = mock(HistoricalPriceReader.class);
        when(reader.read(eq("THYAO"), any(), any())).thenReturn(fullData);

        TechnicalAnalysisResult result = buildService(reader).analyze("THYAO", from, to, "SMA7,SMA20,SMA50,RSI14");

        // Sadece görünür aralık: 20 gün
        assertThat(result.points()).hasSize(20);

        // Tüm noktaların tarihi from..to arasında
        assertThat(result.points()).allMatch(point -> !point.date().isBefore(from) && !point.date().isAfter(to));

        // Warmup noktaları response'a sızmamış olmalı
        assertThat(result.points()).noneMatch(point -> point.date().isBefore(from));
    }

    @Test
    void analyze_should_work_gracefully_when_no_warmup_data_is_available() {
        // Senaryo: veri kaynağı warmup döneminde veri döndüremiyor; yalnızca from..to verisi var.
        // Beklenti: uygulama hata vermemeli; kısa veri için RSI/SMA null olabilir (mevcut davranış).
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = from.plusDays(20);

        // Sadece görünür aralık verisi (warmup yok) — 21 nokta
        List<HistoricalPricePoint> visibleOnly = increasingHistory("BTCUSDT", from, 21);

        HistoricalPriceReader reader = mock(HistoricalPriceReader.class);
        when(reader.read(eq("BTCUSDT"), any(), any())).thenReturn(visibleOnly);

        TechnicalAnalysisResult result = buildService(reader).analyze("BTCUSDT", from, to, "SMA7,SMA20,SMA50,RSI14");

        assertThat(result.analysisStatus()).isEqualTo("AVAILABLE");
        assertThat(result.points()).hasSize(21);

        // 21 nokta: SMA7 hesaplanır (7 < 21), SMA20 hesaplanır (20 < 21), SMA50 null (50 > 21)
        TechnicalAnalysisResult.Point last = result.points().getLast();
        assertThat(last.sma7()).isNotNull();
        assertThat(last.sma20()).isNotNull();
        assertThat(last.sma50()).isNull();
        // RSI14: 21 > 14 so calculated
        assertThat(last.rsi14()).isNotNull();
    }

    @Test
    void analyze_should_filter_out_non_positive_close_prices() {
        // close <= 0 olan noktalar filtrelenmeli
        LocalDate start = LocalDate.of(2026, 1, 1);
        List<HistoricalPricePoint> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            BigDecimal close = (i == 5) ? BigDecimal.ZERO
                    : (i == 12) ? BigDecimal.valueOf(-1)
                    : BigDecimal.valueOf(100 + i);
            history.add(new HistoricalPricePoint("TEST", start.plusDays(i), close));
        }

        HistoricalPriceReader reader = mock(HistoricalPriceReader.class);
        when(reader.read(eq("TEST"), any(), any())).thenReturn(history);

        TechnicalAnalysisResult result = buildService(reader).analyze("TEST", start, start.plusDays(29), "SMA7,RSI14");

        // 2 geçersiz nokta (0 ve -1) filtrelenmiş olmalı
        assertThat(result.points()).hasSize(28);
        assertThat(result.points()).allMatch(point -> point.close().compareTo(BigDecimal.ZERO) > 0);
    }

    // --- Yardımcı metodlar ---

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
        when(historicalPriceReader.read(eq(symbol), any(), any())).thenReturn(history);
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
