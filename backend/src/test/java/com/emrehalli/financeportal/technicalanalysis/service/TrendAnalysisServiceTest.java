package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.technicalanalysis.dto.TrendContext;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisServiceTest {

    private final TrendAnalysisService service = new TrendAnalysisService();

    @Test
    void determineTrend_should_return_sideways_for_null_or_empty_points() {
        assertThat(service.determineTrend(null)).isEqualTo(TrendDirection.SIDEWAYS);
        assertThat(service.determineTrend(List.of())).isEqualTo(TrendDirection.SIDEWAYS);
        assertThat(service.determineTrend(List.of(point(100.0, 95.0, 90.0)))).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void determineTrend_should_return_uptrend_when_5_5_window_rises_above_threshold() {
        // earlier 5 avg = 100, recent 5 avg = 105 (+5%) → shortTermRising (+1)
        // close > sma20 (+1), no sma50 (0) → score = 2 → UPTREND
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(point(100.0 + i, 98.0, 95.0));
        }
        for (int i = 5; i < 10; i++) {
            points.add(point(104.0 + i, 108.0, 95.0));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineTrend_should_return_downtrend_when_5_5_window_falls_below_threshold() {
        // earlier 5 avg = 100, recent 5 avg = 88 (-12%) → shortTermFalling (-1)
        // close < sma20 (-1), no sma50 (0) → score = -2 → DOWNTREND
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(point(100.0 + i, 102.0, 110.0));
        }
        for (int i = 5; i < 10; i++) {
            points.add(point(95.0 - i, 90.0, 110.0));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.DOWNTREND);
    }

    @Test
    void determineTrend_should_return_sideways_when_only_price_signal_without_sma50() {
        // close barely above SMA20 (+1), flat momentum (0), no SMA50 (0) → score = 1 → SIDEWAYS
        // SMA7 no longer contributes; price alone without structural support is insufficient.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(point(100.05, 97.0, 100.0));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void determineTrend_should_return_uptrend_when_sma20_above_sma50_despite_sma7_below_sma20() {
        // SMA7 is no longer scored; sma20 > sma50 (+2) + close > sma20 (+1) + rising (+1) = 4 → UPTREND.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(point(9.50, 9.48, 9.40, 9.30));
        }
        for (int i = 0; i < 5; i++) {
            points.add(point(9.62, 9.54, 9.58, 9.42));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineTrend_should_return_uptrend_when_consolidating_but_sma20_above_sma50() {
        // AUD/TRY core scenario: price in short-term consolidation (shortTermFalling -1)
        // but structural uptrend holds (sma20 > sma50 +2, close > sma20 +1) → score = 2 → UPTREND.
        // Previously returned SIDEWAYS because sma7 < sma20 added an extra -1.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(point(9.65, 9.63, 9.55, 9.42));
        }
        for (int i = 0; i < 5; i++) {
            points.add(point(9.62, 9.54, 9.58, 9.42));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineTrend_should_return_uptrend_with_sma50_structure_and_flat_momentum() {
        // sma20 > sma50 (+2) + close > sma20 (+1) + flat momentum (0) → score = 3 → UPTREND.
        // Structural signals alone (MA20>MA50 + price above MA20) are sufficient.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(point(102.0, 99.0, 100.0, 95.0));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineTrend_should_fall_back_to_two_point_comparison_for_fewer_than_10_points() {
        // 5 points only → fallback (latest >= previous → shortTermRising +1); close > sma20 (+1) → score = 2 → UPTREND
        List<TechnicalAnalysisResult.Point> points = List.of(
                point(100.0, 98.0, 95.0),
                point(101.0, 99.0, 95.0),
                point(102.0, 100.0, 95.0),
                point(103.0, 101.0, 95.0),
                point(104.0, 102.0, 95.0)
        );
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineTrend_should_return_sideways_when_sma_values_are_null() {
        List<TechnicalAnalysisResult.Point> points = List.of(
                point(100, null, null),
                point(101, null, null)
        );
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void buildTrendContext_selectedRangeTrend_should_dampen_spike_at_start_via_window_average() {
        // Senaryo: İlk kapanış spike (110), sonraki 4 gün = 100, son 5 gün = 102.
        // Eski tek-nokta yaklaşımı: (102 - 110) / 110 ≈ -7.3% → DOWNTREND
        // Yeni pencere ortalaması:
        //   leading  = (110+100+100+100+100)/5 = 102
        //   trailing = (102×5)/5 = 102
        //   changePct = 0% → SIDEWAYS: spike aralık bazlı ortalama ile yumuşatıldı.
        // SMA değerleri maTrend hesabını insufficientData=false yapıyor.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        points.add(point(110.0, 108.0, 105.0));                          // spike
        for (int i = 0; i < 4; i++) points.add(point(100.0, 98.0, 95.0));
        for (int i = 0; i < 5; i++) points.add(point(102.0, 100.0, 97.0));

        TrendContext ctx = service.buildTrendContext(points, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(ctx.insufficientData()).isFalse();
        assertThat(ctx.selectedRangeTrend()).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void buildTrendContext_selectedRangeTrend_should_degrade_gracefully_when_fewer_than_5_points() {
        // Senaryo: Sadece 3 geçerli kapanış, pencere boyutundan (5) az.
        // calculateLeadingAverage ve calculateTrailingAverage her ikisi de mevcut
        // 3 noktayı kullanır → aynı ortalama → changePct = 0% → SIDEWAYS.
        // insufficientData = false: veri hesaplanabilir, sadece pencere boyutundan küçük.
        List<TechnicalAnalysisResult.Point> points = List.of(
                point(100.0, 98.0, 95.0),
                point(100.0, 98.0, 95.0),
                point(115.0, 112.0, 109.0)   // son nokta: sma7 > sma20 → maTrend = UPTREND
        );

        TrendContext ctx = service.buildTrendContext(points, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));

        assertThat(ctx.insufficientData()).isFalse();
        assertThat(ctx.dataPoints()).isEqualTo(3);
        assertThat(ctx.selectedRangeTrend()).isEqualTo(TrendDirection.SIDEWAYS);
    }

    // ── Profile-aware trend tests ─────────────────────────────────────────

    @Test
    void determineTrend_fx_profile_treats_0_08_pct_as_rising_because_threshold_is_0_05() {
        // Earlier 5 avg = 100, recent 5 avg = 100.08 (+0.08%).
        // FX threshold = 0.05 → 0.08 > 0.05 → shortTermRising (+1).
        // close > sma20 → +1; sma50 null → 0. Total = 2 → UPTREND.
        // DEFAULT threshold = 0.10 → 0.08 < 0.10 → NOT rising. Total = 1 → SIDEWAYS.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) points.add(point(100.0, 99.0, 98.0));
        for (int i = 0; i < 5; i++) points.add(point(100.08, 99.08, 98.08));

        assertThat(service.determineTrend(points, AnalysisProfile.forType(InstrumentType.FX)))
                .isEqualTo(TrendDirection.UPTREND);
        assertThat(service.determineTrend(points, AnalysisProfile.DEFAULT))
                .isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void determineTrend_crypto_profile_treats_0_3_pct_as_not_rising_because_threshold_is_0_50() {
        // Earlier 5 avg = 100, recent 5 avg = 100.3 (+0.3%).
        // CRYPTO threshold = 0.50 → 0.3 < 0.50 → NOT rising (+0).
        // close > sma20 → +1; sma50 null → 0. Total = 1 → SIDEWAYS.
        // DEFAULT threshold = 0.10 → 0.3 > 0.10 → rising (+1). Total = 2 → UPTREND.
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) points.add(point(100.0, 99.0, 98.0));
        for (int i = 0; i < 5; i++) points.add(point(100.3, 99.3, 99.0));

        assertThat(service.determineTrend(points, AnalysisProfile.forType(InstrumentType.CRYPTO)))
                .isEqualTo(TrendDirection.SIDEWAYS);
        assertThat(service.determineTrend(points, AnalysisProfile.DEFAULT))
                .isEqualTo(TrendDirection.UPTREND);
    }

    @Test
    void determineSignals_crypto_rsi_72_is_neutral_because_overbought_level_is_75() {
        TechnicalAnalysisResult.Point p = point(100.0, 98.0, 95.0, null, 72.0);
        AnalysisProfile crypto = AnalysisProfile.forType(InstrumentType.CRYPTO);

        assertThat(service.determineSignals(p, crypto)).contains(TechnicalSignal.RSI_NEUTRAL);
        assertThat(service.determineSignals(p, crypto)).doesNotContain(TechnicalSignal.RSI_OVERBOUGHT);
    }

    @Test
    void determineSignals_crypto_rsi_76_is_overbought_because_level_is_75() {
        TechnicalAnalysisResult.Point p = point(100.0, 98.0, 95.0, null, 76.0);
        AnalysisProfile crypto = AnalysisProfile.forType(InstrumentType.CRYPTO);

        assertThat(service.determineSignals(p, crypto)).contains(TechnicalSignal.RSI_OVERBOUGHT);
    }

    @Test
    void determineSignals_default_profile_rsi_72_is_overbought() {
        // Default overbought = 70; RSI=72 ≥ 70 → RSI_OVERBOUGHT.
        TechnicalAnalysisResult.Point p = point(100.0, 98.0, 95.0, null, 72.0);

        assertThat(service.determineSignals(p, AnalysisProfile.DEFAULT)).contains(TechnicalSignal.RSI_OVERBOUGHT);
    }

    @Test
    void no_arg_overloads_produce_same_result_as_default_profile() {
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) points.add(point(100.0, 98.0, 95.0));
        for (int i = 0; i < 5; i++) points.add(point(110.0, 108.0, 95.0));
        TechnicalAnalysisResult.Point latest = points.getLast();

        assertThat(service.determineTrend(points))
                .isEqualTo(service.determineTrend(points, AnalysisProfile.DEFAULT));
        assertThat(service.determineSignals(latest))
                .isEqualTo(service.determineSignals(latest, AnalysisProfile.DEFAULT));
    }

    // ── Point helpers ─────────────────────────────────────────────────────

    private static TechnicalAnalysisResult.Point point(double close, Double sma7, Double sma20) {
        return point(close, sma7, sma20, null);
    }

    private static TechnicalAnalysisResult.Point point(double close, Double sma7, Double sma20, Double sma50) {
        return point(close, sma7, sma20, sma50, null);
    }

    private static TechnicalAnalysisResult.Point point(double close, Double sma7, Double sma20, Double sma50, Double rsi14) {
        return new TechnicalAnalysisResult.Point(
                LocalDate.now(),
                BigDecimal.valueOf(close),
                sma7 != null ? BigDecimal.valueOf(sma7) : null,
                sma20 != null ? BigDecimal.valueOf(sma20) : null,
                sma50 != null ? BigDecimal.valueOf(sma50) : null,
                rsi14 != null ? BigDecimal.valueOf(rsi14) : null
        );
    }
}

