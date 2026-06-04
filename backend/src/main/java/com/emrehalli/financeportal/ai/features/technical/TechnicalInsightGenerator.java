package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.features.fundamental.FinancialInsight;

import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class TechnicalInsightGenerator {

    public TechnicalInsight generate(String symbol, TechnicalAnalysisResult analysis) {
        List<FinancialInsight> signals = new ArrayList<>();

        BigDecimal rsi  = analysis.indicatorValues().get(IndicatorType.RSI14);
        BigDecimal sma7  = analysis.indicatorValues().get(IndicatorType.SMA7);
        BigDecimal sma20 = analysis.indicatorValues().get(IndicatorType.SMA20);
        BigDecimal sma50 = analysis.indicatorValues().get(IndicatorType.SMA50);
        BigDecimal price = analysis.latestPrice();
        List<TechnicalSignal> sigs = analysis.signals() != null ? analysis.signals() : List.of();
        TrendDirection trend = analysis.trendDirection();

        addShortTermTrendSignals(signals, sigs, price, sma20);
        addMediumTermSignal(signals, price, sma50);
        addMomentumSignals(signals, sigs, sma7, sma20);
        addRsiSignals(signals, rsi);

        String trendSummary    = buildTrendSummary(trend, sigs);
        String momentumSummary = buildMomentumSummary(rsi, trend, sigs);

        return new TechnicalInsight(List.copyOf(signals), trendSummary, momentumSummary);
    }

    private void addShortTermTrendSignals(List<FinancialInsight> out,
                                          List<TechnicalSignal> sigs,
                                          BigDecimal price,
                                          BigDecimal sma20) {
        if (sigs.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            String detail = price != null && sma20 != null
                    ? " (" + val(price) + " > SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.strength(
                    "KÄ±sa vadeli trend pozitif; fiyat 20 gÃ¼nlÃ¼k ortalama Ã¼zerinde" + detail + "."));
        } else if (sigs.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            String detail = price != null && sma20 != null
                    ? " (" + val(price) + " < SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.weakness(
                    "KÄ±sa vadeli gÃ¶rÃ¼nÃ¼m zayÄ±f; fiyat 20 gÃ¼nlÃ¼k ortalama altÄ±nda" + detail + "."));
        }
    }

    private void addMediumTermSignal(List<FinancialInsight> out, BigDecimal price, BigDecimal sma50) {
        if (price == null || sma50 == null) return;
        if (price.compareTo(sma50) > 0) {
            out.add(FinancialInsight.strength(
                    "Orta vadeli gÃ¶rÃ¼nÃ¼m olumlu; fiyat 50 gÃ¼nlÃ¼k ortalama (" + val(sma50) + ") Ã¼zerinde seyrediyor."));
        } else {
            out.add(FinancialInsight.weakness(
                    "Orta vadeli baskÄ± devam ediyor; fiyat 50 gÃ¼nlÃ¼k ortalama (" + val(sma50) + ") altÄ±nda."));
        }
    }

    private void addMomentumSignals(List<FinancialInsight> out,
                                    List<TechnicalSignal> sigs,
                                    BigDecimal sma7,
                                    BigDecimal sma20) {
        if (sigs.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            String detail = sma7 != null && sma20 != null
                    ? " (SMA7 " + val(sma7) + " > SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.strength(
                    "KÄ±sa vadeli momentum gÃ¼Ã§lÃ¼; 7 gÃ¼nlÃ¼k ortalama 20 gÃ¼nlÃ¼k ortalamanÄ±n Ã¼zerinde" + detail + "."));
        } else if (sigs.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            String detail = sma7 != null && sma20 != null
                    ? " (SMA7 " + val(sma7) + " < SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.weakness(
                    "KÄ±sa vadeli momentum zayÄ±flÄ±yor; 7 gÃ¼nlÃ¼k ortalama 20 gÃ¼nlÃ¼k ortalamanÄ±n altÄ±nda" + detail + "."));
        }
    }

    private void addRsiSignals(List<FinancialInsight> out, BigDecimal rsi) {
        if (rsi == null) return;
        double v = rsi.doubleValue();
        if (v > 70) {
            out.add(FinancialInsight.risk(
                    "AÅŸÄ±rÄ± alÄ±m bÃ¶lgesi; RSI " + val(rsi) + " ile kÄ±sa vadeli yorulma ve dÃ¼zeltme riski artÄ±yor."));
        } else if (v < 30) {
            out.add(FinancialInsight.risk(
                    "AÅŸÄ±rÄ± satÄ±m bÃ¶lgesi; RSI " + val(rsi) + " ile tepki potansiyeli var, ancak dÃ¼ÅŸÃ¼ÅŸ baskÄ±sÄ± sÃ¼rebilir."));
        } else if (v >= 45 && v <= 60) {
            out.add(FinancialInsight.neutral(
                    "Momentum dengeli; RSI " + val(rsi) + " nÃ¶tr bÃ¶lgede â€” yÃ¶n teyidi iÃ§in yeni fiyat hareketi beklenmeli."));
        }
    }

    private String buildTrendSummary(TrendDirection trend, List<TechnicalSignal> sigs) {
        if (trend == null) return "Trend yÃ¶nÃ¼ belirlenemiyor.";
        return switch (trend) {
            case UPTREND -> sigs.contains(TechnicalSignal.PRICE_ABOVE_SMA20)
                    ? "YÃ¼kseliÅŸ trendi ve SMA teyidi mevcut; gÃ¶rÃ¼nÃ¼m olumlu."
                    : "YÃ¼kseliÅŸ trendi var; hareketli ortalama teyidi henÃ¼z gÃ¼Ã§lÃ¼ deÄŸil.";
            case DOWNTREND -> "DÃ¼ÅŸÃ¼ÅŸ trendi; fiyat baskÄ±sÄ± ve artan risk sÃ¶z konusu. Toparlanma iÃ§in ortalama Ã¼zerine dÃ¶nÃ¼ÅŸ izlenmeli.";
            case SIDEWAYS -> "Yatay trend; belirgin bir yÃ¶n sinyali Ã¼retilemiyor, kÄ±rÄ±lÄ±m beklenebilir.";
        };
    }

    private String buildMomentumSummary(BigDecimal rsi, TrendDirection trend, List<TechnicalSignal> sigs) {
        if (rsi == null) return "RSI verisi mevcut deÄŸil; momentum sinyallerle sÄ±nÄ±rlÄ± yorumlanÄ±yor.";
        double v = rsi.doubleValue();
        if (v > 70) return "RSI aÅŸÄ±rÄ± alÄ±m bÃ¶lgesinde (" + val(rsi) + "); kÄ±sa vadeli dÃ¼zeltme riski artmÄ±ÅŸ durumda.";
        if (v < 30) return "RSI aÅŸÄ±rÄ± satÄ±m bÃ¶lgesinde (" + val(rsi) + "); tepki potansiyeli mevcut, ancak risk yÃ¼ksek.";
        if (trend == TrendDirection.UPTREND || sigs.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            return "RSI nÃ¶tr bÃ¶lgede (" + val(rsi) + "); trend yukarÄ± â€” aÅŸÄ±rÄ± alÄ±m sinyali olmaksÄ±zÄ±n momentum olumlu.";
        }
        if (trend == TrendDirection.DOWNTREND || sigs.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            return "RSI nÃ¶tr bÃ¶lgede (" + val(rsi) + ") olsa da trend zayÄ±f; momentum baskÄ± altÄ±nda.";
        }
        return "RSI " + val(rsi) + " nÃ¶tr bÃ¶lgede; momentum dengeli, yeni fiyat hareketi bekleniyor.";
    }

    private String val(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}




