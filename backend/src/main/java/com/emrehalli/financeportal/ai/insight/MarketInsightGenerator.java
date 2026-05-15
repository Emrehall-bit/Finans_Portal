package com.emrehalli.financeportal.ai.insight;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Generates pre-interpreted market breadth and sentiment insights
 * from aggregate market statistics (e.g., average change, rising/falling counts).
 *
 * Filters out anomalous values (-100%, zero-price, nulls) before interpreting.
 */
@Component
public class MarketInsightGenerator {

    /**
     * @param averageChangePercent average % change across instruments (already cleaned of anomalies)
     * @param risingCount          number of instruments with positive change
     * @param fallingCount         number of instruments with negative change
     */
    public MarketInsight generate(BigDecimal averageChangePercent, int risingCount, int fallingCount) {
        String sentimentSummary = buildSentimentSummary(averageChangePercent);
        String breadthSummary   = buildBreadthSummary(risingCount, fallingCount);
        return new MarketInsight(sentimentSummary, breadthSummary);
    }

    private String buildSentimentSummary(BigDecimal avgChange) {
        if (avgChange == null) return "Piyasa geneli değişim verisi mevcut değil.";
        double v = avgChange.doubleValue();
        if (v < -2.0) return "Piyasada belirgin satış baskısı; risk iştahı zayıf (ort. " + fmt(avgChange) + "%).";
        if (v < -1.0) return "Piyasada risk iştahı zayıf; ortalama değişim " + fmt(avgChange) + "%.";
        if (v < -0.3) return "Piyasada hafif satış baskısı; ortalama değişim " + fmt(avgChange) + "%.";
        if (v < 0.3)  return "Piyasa yatay seyrediyor; ortalama değişim " + fmt(avgChange) + "%.";
        if (v < 1.0)  return "Piyasada hafif pozitif görünüm; ortalama değişim +" + fmt(avgChange) + "%.";
        if (v < 2.0)  return "Piyasa genelinde olumlu görünüm; ortalama değişim +" + fmt(avgChange) + "%.";
        return "Piyasada güçlü alım baskısı; risk iştahı yüksek, ortalama değişim +" + fmt(avgChange) + "%.";
    }

    private String buildBreadthSummary(int risingCount, int fallingCount) {
        int total = risingCount + fallingCount;
        if (total == 0) return "Piyasa genişliği verisi mevcut değil.";
        double risingRatio = (double) risingCount / total;
        if (risingRatio > 0.70) {
            return "Yükselenler belirgin üstünlükte (" + risingCount + "/" + total + "); geniş tabanlı pozitif momentum.";
        }
        if (risingRatio > 0.55) {
            return "Yükselenler çoğunlukta (" + risingCount + "/" + total + "); genel eğilim olumlu.";
        }
        if (risingRatio < 0.30) {
            return "Düşenler belirgin üstünlükte (" + fallingCount + "/" + total + "); geniş tabanlı satış baskısı.";
        }
        if (risingRatio < 0.45) {
            return "Düşenler çoğunlukta (" + fallingCount + "/" + total + "); genel eğilim olumsuz.";
        }
        return "Yükselen ve düşen enstrüman sayısı dengeli (" + risingCount + " yükselen, " + fallingCount + " düşen).";
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "-";
        return String.format("%.2f", value.doubleValue());
    }
}
