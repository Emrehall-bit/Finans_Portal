package com.emrehalli.financeportal.ai.insight;

import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
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
                    "Kısa vadeli trend pozitif; fiyat 20 günlük ortalama üzerinde" + detail + "."));
        } else if (sigs.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            String detail = price != null && sma20 != null
                    ? " (" + val(price) + " < SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.weakness(
                    "Kısa vadeli görünüm zayıf; fiyat 20 günlük ortalama altında" + detail + "."));
        }
    }

    private void addMediumTermSignal(List<FinancialInsight> out, BigDecimal price, BigDecimal sma50) {
        if (price == null || sma50 == null) return;
        if (price.compareTo(sma50) > 0) {
            out.add(FinancialInsight.strength(
                    "Orta vadeli görünüm olumlu; fiyat 50 günlük ortalama (" + val(sma50) + ") üzerinde seyrediyor."));
        } else {
            out.add(FinancialInsight.weakness(
                    "Orta vadeli baskı devam ediyor; fiyat 50 günlük ortalama (" + val(sma50) + ") altında."));
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
                    "Kısa vadeli momentum güçlü; 7 günlük ortalama 20 günlük ortalamanın üzerinde" + detail + "."));
        } else if (sigs.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            String detail = sma7 != null && sma20 != null
                    ? " (SMA7 " + val(sma7) + " < SMA20 " + val(sma20) + ")" : "";
            out.add(FinancialInsight.weakness(
                    "Kısa vadeli momentum zayıflıyor; 7 günlük ortalama 20 günlük ortalamanın altında" + detail + "."));
        }
    }

    private void addRsiSignals(List<FinancialInsight> out, BigDecimal rsi) {
        if (rsi == null) return;
        double v = rsi.doubleValue();
        if (v > 70) {
            out.add(FinancialInsight.risk(
                    "Aşırı alım bölgesi; RSI " + val(rsi) + " ile kısa vadeli yorulma ve düzeltme riski artıyor."));
        } else if (v < 30) {
            out.add(FinancialInsight.risk(
                    "Aşırı satım bölgesi; RSI " + val(rsi) + " ile tepki potansiyeli var, ancak düşüş baskısı sürebilir."));
        } else if (v >= 45 && v <= 60) {
            out.add(FinancialInsight.neutral(
                    "Momentum dengeli; RSI " + val(rsi) + " nötr bölgede — yön teyidi için yeni fiyat hareketi beklenmeli."));
        }
    }

    private String buildTrendSummary(TrendDirection trend, List<TechnicalSignal> sigs) {
        if (trend == null) return "Trend yönü belirlenemiyor.";
        return switch (trend) {
            case UPTREND -> sigs.contains(TechnicalSignal.PRICE_ABOVE_SMA20)
                    ? "Yükseliş trendi ve SMA teyidi mevcut; görünüm olumlu."
                    : "Yükseliş trendi var; hareketli ortalama teyidi henüz güçlü değil.";
            case DOWNTREND -> "Düşüş trendi; fiyat baskısı ve artan risk söz konusu. Toparlanma için ortalama üzerine dönüş izlenmeli.";
            case SIDEWAYS -> "Yatay trend; belirgin bir yön sinyali üretilemiyor, kırılım beklenebilir.";
        };
    }

    private String buildMomentumSummary(BigDecimal rsi, TrendDirection trend, List<TechnicalSignal> sigs) {
        if (rsi == null) return "RSI verisi mevcut değil; momentum sinyallerle sınırlı yorumlanıyor.";
        double v = rsi.doubleValue();
        if (v > 70) return "RSI aşırı alım bölgesinde (" + val(rsi) + "); kısa vadeli düzeltme riski artmış durumda.";
        if (v < 30) return "RSI aşırı satım bölgesinde (" + val(rsi) + "); tepki potansiyeli mevcut, ancak risk yüksek.";
        if (trend == TrendDirection.UPTREND || sigs.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            return "RSI nötr bölgede (" + val(rsi) + "); trend yukarı — aşırı alım sinyali olmaksızın momentum olumlu.";
        }
        if (trend == TrendDirection.DOWNTREND || sigs.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            return "RSI nötr bölgede (" + val(rsi) + ") olsa da trend zayıf; momentum baskı altında.";
        }
        return "RSI " + val(rsi) + " nötr bölgede; momentum dengeli, yeni fiyat hareketi bekleniyor.";
    }

    private String val(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}
