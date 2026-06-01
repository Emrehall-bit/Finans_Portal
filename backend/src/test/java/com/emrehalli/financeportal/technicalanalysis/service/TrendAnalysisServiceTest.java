package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.dto.TrendContext;
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
        // earlier 5 points avg = 100, recent 5 points avg = 105 (+5%) â†’ rising
        // latest: price > sma20, sma7 > sma20 â†’ UPTREND
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
        // earlier 5 points avg = 100, recent 5 points avg = 92 (-8%) â†’ falling
        // latest: price < sma20, sma7 < sma20 â†’ DOWNTREND
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
    void determineTrend_should_return_sideways_when_change_is_below_threshold() {
        // earlier 5 avg = 100, recent 5 avg = 100.05 (0.05%) â€” below 0.1% threshold
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(point(100.0, 102.0, 98.0));
        }
        for (int i = 0; i < 5; i++) {
            points.add(point(100.05, 102.0, 98.0));
        }
        assertThat(service.determineTrend(points)).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void determineTrend_should_fall_back_to_two_point_comparison_for_fewer_than_10_points() {
        // 5 points only â†’ fallback; latest >= previous, price > sma20, sma7 > sma20 â†’ UPTREND
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

    private static TechnicalAnalysisResult.Point point(double close, Double sma7, Double sma20) {
        return new TechnicalAnalysisResult.Point(
                LocalDate.now(),
                BigDecimal.valueOf(close),
                sma7 != null ? BigDecimal.valueOf(sma7) : null,
                sma20 != null ? BigDecimal.valueOf(sma20) : null,
                null,
                null
        );
    }
}

