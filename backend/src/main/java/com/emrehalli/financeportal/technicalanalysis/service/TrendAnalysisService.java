package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.dto.TrendContext;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrendAnalysisService {

    private static final int RANGE_WINDOW_SIZE = 5;

    /** Backward-compatible overload; uses DEFAULT profile (original thresholds). */
    public TrendDirection determineTrend(List<TechnicalAnalysisResult.Point> points) {
        return determineTrend(points, AnalysisProfile.DEFAULT);
    }

    /**
     * Determines trend direction using a three-signal weighted scoring system.
     *
     * Scoring:
     *   close > sma20   → +1 / close < sma20  → -1   (price vs medium-term average)
     *   sma20 > sma50   → +2 / sma20 < sma50  → -2   (structural trend; skipped when sma50 unavailable)
     *   shortTermRising → +1 / shortTermFalling → -1  (5+5 window momentum, or two-point fallback)
     *
     * SMA7 is intentionally excluded: it is too short-term and causes false SIDEWAYS
     * readings during consolidations in instruments with a clear medium-term uptrend.
     *
     * score ≥ 2  → UPTREND
     * score ≤ -2 → DOWNTREND
     * otherwise  → SIDEWAYS
     *
     * The momentum threshold comes from the supplied profile so that FX, CRYPTO, etc.
     * can use asset-class-appropriate sensitivity levels.
     */
    public TrendDirection determineTrend(List<TechnicalAnalysisResult.Point> points, AnalysisProfile profile) {
        if (points == null || points.size() < 2) {
            return TrendDirection.SIDEWAYS;
        }

        TechnicalAnalysisResult.Point latestPoint = points.getLast();

        if (latestPoint.close() == null || latestPoint.sma20() == null) {
            return TrendDirection.SIDEWAYS;
        }

        int score = 0;

        int closeSma20 = latestPoint.close().compareTo(latestPoint.sma20());
        if (closeSma20 > 0) score += 1;
        else if (closeSma20 < 0) score -= 1;

        if (latestPoint.sma50() != null) {
            int sma20Sma50 = latestPoint.sma20().compareTo(latestPoint.sma50());
            if (sma20Sma50 > 0) score += 2;
            else if (sma20Sma50 < 0) score -= 2;
        }

        boolean shortTermRising;
        boolean shortTermFalling;

        if (points.size() >= 10) {
            int size = points.size();
            BigDecimal recent = windowAverage(points, size - 5, size);
            BigDecimal earlier = windowAverage(points, size - 10, size - 5);
            if (recent == null || earlier == null || earlier.signum() == 0) {
                shortTermRising = false;
                shortTermFalling = false;
            } else {
                BigDecimal changePct = recent.subtract(earlier)
                        .divide(earlier.abs(), 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                BigDecimal threshold = profile.shortTermMomentumThresholdPct();
                shortTermRising = changePct.compareTo(threshold) > 0;
                shortTermFalling = changePct.compareTo(threshold.negate()) < 0;
            }
        } else {
            TechnicalAnalysisResult.Point previousPoint = points.get(points.size() - 2);
            if (previousPoint.close() == null) {
                shortTermRising = false;
                shortTermFalling = false;
            } else {
                int cmp = latestPoint.close().compareTo(previousPoint.close());
                shortTermRising = cmp >= 0;
                shortTermFalling = cmp <= 0;
            }
        }

        if (shortTermRising) score += 1;
        else if (shortTermFalling) score -= 1;

        if (score >= 2) return TrendDirection.UPTREND;
        if (score <= -2) return TrendDirection.DOWNTREND;
        return TrendDirection.SIDEWAYS;
    }

    /** Backward-compatible overload; uses DEFAULT profile (original thresholds). */
    public TrendContext buildTrendContext(List<TechnicalAnalysisResult.Point> points, LocalDate from, LocalDate to) {
        return buildTrendContext(points, from, to, AnalysisProfile.DEFAULT);
    }

    public TrendContext buildTrendContext(List<TechnicalAnalysisResult.Point> points, LocalDate from, LocalDate to, AnalysisProfile profile) {
        TrendDirection shortTermTrend = determineTrend(points, profile);
        String rangeLabel = resolveRangeLabel(from, to);
        int dataPoints = points == null ? 0 : points.size();
        boolean insufficientData = false;

        TrendDirection selectedRangeTrend;
        if (points == null || points.size() < 2) {
            selectedRangeTrend = TrendDirection.SIDEWAYS;
            insufficientData = true;
        } else {
            BigDecimal startAvg = calculateLeadingAverage(points, RANGE_WINDOW_SIZE);
            BigDecimal endAvg = calculateTrailingAverage(points, RANGE_WINDOW_SIZE);
            if (startAvg == null || startAvg.signum() <= 0 || endAvg == null || endAvg.signum() <= 0) {
                selectedRangeTrend = TrendDirection.SIDEWAYS;
                insufficientData = true;
            } else {
                BigDecimal changePct = endAvg.subtract(startAvg)
                        .divide(startAvg.abs(), 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                BigDecimal rangeThreshold = profile.rangeTrendThresholdPct();
                selectedRangeTrend = changePct.compareTo(rangeThreshold) > 0
                        ? TrendDirection.UPTREND
                        : changePct.compareTo(rangeThreshold.negate()) < 0
                                ? TrendDirection.DOWNTREND
                                : TrendDirection.SIDEWAYS;
            }
        }

        TrendDirection maTrend;
        if (points == null || points.isEmpty()) {
            maTrend = TrendDirection.SIDEWAYS;
            insufficientData = true;
        } else {
            TechnicalAnalysisResult.Point latestPoint = points.getLast();
            BigDecimal sma7 = latestPoint.sma7();
            BigDecimal sma20 = latestPoint.sma20();
            if (sma7 == null || sma20 == null) {
                maTrend = TrendDirection.SIDEWAYS;
                insufficientData = true;
            } else {
                int cmp = sma7.compareTo(sma20);
                maTrend = cmp > 0 ? TrendDirection.UPTREND : cmp < 0 ? TrendDirection.DOWNTREND : TrendDirection.SIDEWAYS;
            }
        }

        return new TrendContext(shortTermTrend, selectedRangeTrend, maTrend, rangeLabel, dataPoints, insufficientData);
    }

    private static boolean validClose(BigDecimal close) {
        return close != null && close.signum() > 0;
    }

    private BigDecimal calculateLeadingAverage(List<TechnicalAnalysisResult.Point> points, int window) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (TechnicalAnalysisResult.Point point : points) {
            if (count >= window) break;
            if (validClose(point.close())) {
                sum = sum.add(point.close());
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTrailingAverage(List<TechnicalAnalysisResult.Point> points, int window) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = points.size() - 1; i >= 0 && count < window; i--) {
            if (validClose(points.get(i).close())) {
                sum = sum.add(points.get(i).close());
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    private static String resolveRangeLabel(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "MAX";
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 10) return "7D";
        if (days <= 45) return "1M";
        if (days <= 120) return "3M";
        if (days <= 220) return "6M";
        if (days <= 400) return "1Y";
        return "MAX";
    }

    private BigDecimal windowAverage(List<TechnicalAnalysisResult.Point> points, int fromInclusive, int toExclusive) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int index = fromInclusive; index < toExclusive; index++) {
            BigDecimal close = points.get(index).close();
            if (close != null) {
                sum = sum.add(close);
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    /** Backward-compatible overload; uses DEFAULT profile (original RSI 70/30 levels). */
    public List<TechnicalSignal> determineSignals(TechnicalAnalysisResult.Point latestPoint) {
        return determineSignals(latestPoint, AnalysisProfile.DEFAULT);
    }

    public List<TechnicalSignal> determineSignals(TechnicalAnalysisResult.Point latestPoint, AnalysisProfile profile) {
        if (latestPoint == null) {
            return List.of();
        }

        List<TechnicalSignal> signals = new ArrayList<>();
        BigDecimal close = latestPoint.close();
        BigDecimal sma7 = latestPoint.sma7();
        BigDecimal sma20 = latestPoint.sma20();
        BigDecimal rsi14 = latestPoint.rsi14();

        if (close != null && sma20 != null) {
            if (close.compareTo(sma20) > 0) {
                signals.add(TechnicalSignal.PRICE_ABOVE_SMA20);
            } else if (close.compareTo(sma20) < 0) {
                signals.add(TechnicalSignal.PRICE_BELOW_SMA20);
            }
        }

        if (sma7 != null && sma20 != null) {
            if (sma7.compareTo(sma20) > 0) {
                signals.add(TechnicalSignal.SMA7_ABOVE_SMA20);
            } else if (sma7.compareTo(sma20) < 0) {
                signals.add(TechnicalSignal.SMA7_BELOW_SMA20);
            }
        }

        if (rsi14 != null) {
            if (rsi14.compareTo(profile.rsiOverboughtLevel()) >= 0) {
                signals.add(TechnicalSignal.RSI_OVERBOUGHT);
            } else if (rsi14.compareTo(profile.rsiOversoldLevel()) <= 0) {
                signals.add(TechnicalSignal.RSI_OVERSOLD);
            } else {
                signals.add(TechnicalSignal.RSI_NEUTRAL);
            }
        }

        return List.copyOf(signals);
    }
}




