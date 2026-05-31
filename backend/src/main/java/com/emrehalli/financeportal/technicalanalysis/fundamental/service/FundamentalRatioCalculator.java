package com.emrehalli.financeportal.technicalanalysis.fundamental.service;

import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.CompanyFinancials;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalRatios;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Temel analiz oran ve score'larını hesaplayan, state taşımayan saf hesap sınıfı. Repository veya
 * Service bağımlılığı <b>almaz</b>; yalnızca verilen girdilerden türetilen değerleri hesaplar ve
 * sonucu {@link FundamentalRatios} nesnesine yazar.
 *
 * <p>{@code currentPrice} ve {@code sharesOutstanding}, {@code FundamentalAnalysisService} tarafından
 * repo'lardan çözülüp parametre olarak geçirilir; bu sınıf veri okumaz. BigDecimal scale/rounding
 * (SCALE=4, HALF_UP), null propagation, hesaplama sırası ve sinyal üretimi, daha önce servis içinde
 * olan davranışla birebir aynıdır (golden testlerle kilitli).
 */
@Component
public class FundamentalRatioCalculator {

    private static final int SCALE = 4;

    /**
     * Verilen finansallardan tüm oranları/score'ları hesaplar ve {@code ratios} üzerine yazar.
     * Mevcut hesaplama sırası ve koşulları (eps>0, bvps>0, totalAssets>0 vb.) birebir korunur.
     */
    public void populate(FundamentalRatios ratios,
                         CompanyFinancials financials,
                         List<CompanyFinancials> annualHistory,
                         BigDecimal currentPrice,
                         BigDecimal sharesOutstanding) {
        ratios.setGrossMargin(pct(financials.getGrossProfit(), financials.getRevenue()));
        ratios.setNetMargin(pct(financials.getNetIncome(), financials.getRevenue()));
        ratios.setRoe(pct(financials.getNetIncome(), financials.getTotalEquity()));
        ratios.setRoa(pct(financials.getNetIncome(), financials.getTotalAssets()));
        ratios.setDebtToEquity(ratio(financials.getTotalLiabilities(), financials.getTotalEquity()));
        ratios.setCurrentRatio(ratio(financials.getCurrentAssets(), financials.getCurrentLiabilities()));

        if (sharesOutstanding != null && sharesOutstanding.compareTo(BigDecimal.ZERO) > 0
                && financials.getNetIncome() != null) {
            BigDecimal eps = financials.getNetIncome().divide(sharesOutstanding, SCALE, RoundingMode.HALF_UP);
            if (eps.compareTo(BigDecimal.ZERO) > 0) {
                ratios.setPeRatio(currentPrice.divide(eps, SCALE, RoundingMode.HALF_UP));
            }
            BigDecimal bookValuePerShare = financials.getTotalEquity() != null
                    ? financials.getTotalEquity().divide(sharesOutstanding, SCALE, RoundingMode.HALF_UP)
                    : null;
            if (bookValuePerShare != null && bookValuePerShare.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal marketCap = currentPrice.multiply(sharesOutstanding);
                ratios.setPbRatio(marketCap.divide(financials.getTotalEquity(), SCALE, RoundingMode.HALF_UP));

                BigDecimal grahamSquared = BigDecimal.valueOf(22.5)
                        .multiply(eps.max(BigDecimal.ZERO))
                        .multiply(bookValuePerShare.max(BigDecimal.ZERO));
                if (grahamSquared.compareTo(BigDecimal.ZERO) > 0) {
                    ratios.setGrahamNumber(BigDecimal.valueOf(Math.sqrt(grahamSquared.doubleValue()))
                            .setScale(SCALE, RoundingMode.HALF_UP));
                }

                if (financials.getTotalAssets() != null && financials.getTotalAssets().compareTo(BigDecimal.ZERO) > 0
                        && financials.getTotalLiabilities() != null) {
                    BigDecimal totalAssets = financials.getTotalAssets();
                    BigDecimal workingCapital = safeSubtract(financials.getCurrentAssets(), financials.getCurrentLiabilities());
                    BigDecimal x1 = workingCapital != null ? workingCapital.divide(totalAssets, SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    BigDecimal x2 = BigDecimal.ZERO;
                    BigDecimal x3 = financials.getNetIncome().divide(totalAssets, SCALE, RoundingMode.HALF_UP);
                    BigDecimal marketCapValue = currentPrice.multiply(sharesOutstanding);
                    BigDecimal x4 = financials.getTotalLiabilities().compareTo(BigDecimal.ZERO) > 0
                            ? marketCapValue.divide(financials.getTotalLiabilities(), SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    BigDecimal x5 = financials.getRevenue() != null
                            ? financials.getRevenue().divide(totalAssets, SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    BigDecimal z = BigDecimal.valueOf(1.2).multiply(x1)
                            .add(BigDecimal.valueOf(1.4).multiply(x2))
                            .add(BigDecimal.valueOf(3.3).multiply(x3))
                            .add(BigDecimal.valueOf(0.6).multiply(x4))
                            .add(BigDecimal.valueOf(1.0).multiply(x5));
                    ratios.setAltmanZScore(z.setScale(SCALE, RoundingMode.HALF_UP));
                }
            }
        }

        int currentIndex = annualHistory.indexOf(financials);
        if (currentIndex >= 0 && currentIndex + 1 < annualHistory.size()) {
            CompanyFinancials prev = annualHistory.get(currentIndex + 1);
            ratios.setRevenueGrowthYoy(yoyGrowth(financials.getRevenue(), prev.getRevenue()));
            ratios.setNetIncomeGrowthYoy(yoyGrowth(financials.getNetIncome(), prev.getNetIncome()));
            ratios.setAssetGrowthYoy(yoyGrowth(financials.getTotalAssets(), prev.getTotalAssets()));
            ratios.setPiotroskiScore(calculatePiotroski(financials, prev));
        }

        ratios.setOverallSignal(determineSignal(ratios));
    }

    private int calculatePiotroski(CompanyFinancials current, CompanyFinancials prev) {
        int score = 0;
        if (isPositive(current.getNetIncome()) && isPositive(current.getTotalAssets())) score++;
        if (isPositive(current.getOperatingCashFlow())) score++;
        double roa = safeRatio(current.getNetIncome(), current.getTotalAssets());
        double prevRoa = safeRatio(prev.getNetIncome(), prev.getTotalAssets());
        if (roa > prevRoa) score++;
        if (current.getOperatingCashFlow() != null && current.getNetIncome() != null
                && current.getOperatingCashFlow().compareTo(current.getNetIncome()) > 0) score++;
        if (current.getTotalLiabilities() != null && prev.getTotalLiabilities() != null
                && current.getTotalLiabilities().compareTo(prev.getTotalLiabilities()) < 0) score++;
        double cr = safeRatio(current.getCurrentAssets(), current.getCurrentLiabilities());
        double prevCr = safeRatio(prev.getCurrentAssets(), prev.getCurrentLiabilities());
        if (cr > prevCr) score++;
        score++;
        double gm = safeRatio(current.getGrossProfit(), current.getRevenue());
        double prevGm = safeRatio(prev.getGrossProfit(), prev.getRevenue());
        if (gm > prevGm) score++;
        double at = safeRatio(current.getRevenue(), current.getTotalAssets());
        double prevAt = safeRatio(prev.getRevenue(), prev.getTotalAssets());
        if (at > prevAt) score++;
        return Math.min(score, 9);
    }

    private String determineSignal(FundamentalRatios ratios) {
        boolean bullish = ratios.getPeRatio() != null && ratios.getPeRatio().compareTo(BigDecimal.valueOf(20)) < 0
                && ratios.getRoe() != null && ratios.getRoe().compareTo(BigDecimal.valueOf(15)) > 0
                && ratios.getPiotroskiScore() != null && ratios.getPiotroskiScore() >= 6;
        if (bullish) return "BULLISH";
        if (ratios.getPiotroskiScore() != null && ratios.getPiotroskiScore() <= 3) return "BEARISH";
        return "NEUTRAL";
    }

    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal yoyGrowth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), SCALE, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private double safeRatio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.subtract(b);
    }
}
