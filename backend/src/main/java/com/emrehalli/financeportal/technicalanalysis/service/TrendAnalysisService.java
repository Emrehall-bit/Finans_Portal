package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrendAnalysisService {

    public TrendDirection determineTrend(TechnicalAnalysisPoint previousPoint, TechnicalAnalysisPoint latestPoint) {
        if (previousPoint == null || latestPoint == null
                || latestPoint.close() == null
                || previousPoint.close() == null
                || latestPoint.sma20() == null
                || latestPoint.sma7() == null) {
            return TrendDirection.SIDEWAYS;
        }

        boolean priceAboveSma20 = latestPoint.close().compareTo(latestPoint.sma20()) > 0;
        boolean sma7AboveSma20 = latestPoint.sma7().compareTo(latestPoint.sma20()) > 0;
        boolean latestPriceNotBelowPrevious = latestPoint.close().compareTo(previousPoint.close()) >= 0;
        boolean priceBelowSma20 = latestPoint.close().compareTo(latestPoint.sma20()) < 0;
        boolean sma7BelowSma20 = latestPoint.sma7().compareTo(latestPoint.sma20()) < 0;
        boolean latestPriceNotAbovePrevious = latestPoint.close().compareTo(previousPoint.close()) <= 0;

        if (priceAboveSma20 && sma7AboveSma20 && latestPriceNotBelowPrevious) {
            return TrendDirection.UPTREND;
        }

        if (priceBelowSma20 && sma7BelowSma20 && latestPriceNotAbovePrevious) {
            return TrendDirection.DOWNTREND;
        }

        return TrendDirection.SIDEWAYS;
    }

    public List<TechnicalSignal> determineSignals(TechnicalAnalysisPoint latestPoint) {
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
