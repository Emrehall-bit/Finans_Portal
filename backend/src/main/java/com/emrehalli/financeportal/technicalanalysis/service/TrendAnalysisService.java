package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrendAnalysisService {

    private static final BigDecimal TREND_THRESHOLD_PCT = BigDecimal.valueOf(0.1);

    /**
     * Determines trend direction using a 5+5 closing-price window when enough data is available.
     * Falls back to a two-point comparison when fewer than 10 points exist.
     */
    public TrendDirection determineTrend(List<TechnicalAnalysisResult.Point> points) {
        if (points == null || points.size() < 2) {
            return TrendDirection.SIDEWAYS;
        }

        TechnicalAnalysisResult.Point latestPoint = points.getLast();

        if (latestPoint.close() == null || latestPoint.sma20() == null || latestPoint.sma7() == null) {
            return TrendDirection.SIDEWAYS;
        }

        boolean priceAboveSma20 = latestPoint.close().compareTo(latestPoint.sma20()) > 0;
        boolean sma7AboveSma20 = latestPoint.sma7().compareTo(latestPoint.sma20()) > 0;
        boolean priceBelowSma20 = latestPoint.close().compareTo(latestPoint.sma20()) < 0;
        boolean sma7BelowSma20 = latestPoint.sma7().compareTo(latestPoint.sma20()) < 0;

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
                shortTermRising = changePct.compareTo(TREND_THRESHOLD_PCT) > 0;
                shortTermFalling = changePct.compareTo(TREND_THRESHOLD_PCT.negate()) < 0;
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

        if (priceAboveSma20 && sma7AboveSma20 && shortTermRising) {
            return TrendDirection.UPTREND;
        }
        if (priceBelowSma20 && sma7BelowSma20 && shortTermFalling) {
            return TrendDirection.DOWNTREND;
        }
        return TrendDirection.SIDEWAYS;
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

    public List<TechnicalSignal> determineSignals(TechnicalAnalysisResult.Point latestPoint) {
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
            if (rsi14.compareTo(BigDecimal.valueOf(70)) >= 0) {
                signals.add(TechnicalSignal.RSI_OVERBOUGHT);
            } else if (rsi14.compareTo(BigDecimal.valueOf(30)) <= 0) {
                signals.add(TechnicalSignal.RSI_OVERSOLD);
            } else {
                signals.add(TechnicalSignal.RSI_NEUTRAL);
            }
        }

        return List.copyOf(signals);
    }
}




