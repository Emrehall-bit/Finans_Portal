package com.emrehalli.financeportal.ai.features.unified;

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
        if (avgChange == null) return "Piyasa geneli deÄŸiÅŸim verisi mevcut deÄŸil.";
        double v = avgChange.doubleValue();
        if (v < -2.0) return "Piyasada belirgin satÄ±ÅŸ baskÄ±sÄ±; risk iÅŸtahÄ± zayÄ±f (ort. " + fmt(avgChange) + "%).";
        if (v < -1.0) return "Piyasada risk iÅŸtahÄ± zayÄ±f; ortalama deÄŸiÅŸim " + fmt(avgChange) + "%.";
        if (v < -0.3) return "Piyasada hafif satÄ±ÅŸ baskÄ±sÄ±; ortalama deÄŸiÅŸim " + fmt(avgChange) + "%.";
        if (v < 0.3)  return "Piyasa yatay seyrediyor; ortalama deÄŸiÅŸim " + fmt(avgChange) + "%.";
        if (v < 1.0)  return "Piyasada hafif pozitif gÃ¶rÃ¼nÃ¼m; ortalama deÄŸiÅŸim +" + fmt(avgChange) + "%.";
        if (v < 2.0)  return "Piyasa genelinde olumlu gÃ¶rÃ¼nÃ¼m; ortalama deÄŸiÅŸim +" + fmt(avgChange) + "%.";
        return "Piyasada gÃ¼Ã§lÃ¼ alÄ±m baskÄ±sÄ±; risk iÅŸtahÄ± yÃ¼ksek, ortalama deÄŸiÅŸim +" + fmt(avgChange) + "%.";
    }

    private String buildBreadthSummary(int risingCount, int fallingCount) {
        int total = risingCount + fallingCount;
        if (total == 0) return "Piyasa geniÅŸliÄŸi verisi mevcut deÄŸil.";
        double risingRatio = (double) risingCount / total;
        if (risingRatio > 0.70) {
            return "YÃ¼kselenler belirgin Ã¼stÃ¼nlÃ¼kte (" + risingCount + "/" + total + "); geniÅŸ tabanlÄ± pozitif momentum.";
        }
        if (risingRatio > 0.55) {
            return "YÃ¼kselenler Ã§oÄŸunlukta (" + risingCount + "/" + total + "); genel eÄŸilim olumlu.";
        }
        if (risingRatio < 0.30) {
            return "DÃ¼ÅŸenler belirgin Ã¼stÃ¼nlÃ¼kte (" + fallingCount + "/" + total + "); geniÅŸ tabanlÄ± satÄ±ÅŸ baskÄ±sÄ±.";
        }
        if (risingRatio < 0.45) {
            return "DÃ¼ÅŸenler Ã§oÄŸunlukta (" + fallingCount + "/" + total + "); genel eÄŸilim olumsuz.";
        }
        return "YÃ¼kselen ve dÃ¼ÅŸen enstrÃ¼man sayÄ±sÄ± dengeli (" + risingCount + " yÃ¼kselen, " + fallingCount + " dÃ¼ÅŸen).";
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "-";
        return String.format("%.2f", value.doubleValue());
    }
}




