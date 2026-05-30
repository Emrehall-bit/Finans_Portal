package com.emrehalli.financeportal.technicalanalysis.fundamental.service;

import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FinancialDataResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalHistoryPoint;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalRatiosResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.CompanyFinancials;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalHistory;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalRatios;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalRatiosRepository;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class FundamentalAnalysisService {

    private static final Logger logger = LogManager.getLogger(FundamentalAnalysisService.class);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 4;

    private final CompanyFinancialsRepository companyFinancialsRepository;
    private final FundamentalRatiosRepository fundamentalRatiosRepository;
    private final FundamentalHistoryRepository fundamentalHistoryRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final CompanyProfileRepository companyProfileRepository;

    public FundamentalAnalysisService(CompanyFinancialsRepository companyFinancialsRepository,
                                      FundamentalRatiosRepository fundamentalRatiosRepository,
                                      FundamentalHistoryRepository fundamentalHistoryRepository,
                                      MarketInstrumentRepository marketInstrumentRepository,
                                      MarketPriceHistoryRepository marketPriceHistoryRepository,
                                      CompanyProfileRepository companyProfileRepository) {
        this.companyFinancialsRepository = companyFinancialsRepository;
        this.fundamentalRatiosRepository = fundamentalRatiosRepository;
        this.fundamentalHistoryRepository = fundamentalHistoryRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
        this.companyProfileRepository = companyProfileRepository;
    }

    @Transactional(readOnly = true)
    public FundamentalRatiosResponse getLatestRatios(String instrumentCode, boolean premium) {
        MarketInstrument instrument = resolveInstrument(instrumentCode);
        FundamentalRatios ratios = fundamentalRatiosRepository
                .findTopByInstrumentIdOrderByCalculatedAtDesc(instrument.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Temel analiz verisi bulunamadı: " + instrumentCode));

        return toRatiosResponse(ratios, premium);
    }

    @Transactional(readOnly = true)
    public List<FundamentalHistoryPoint> getHistory(String instrumentCode) {
        MarketInstrument instrument = resolveInstrument(instrumentCode);
        return fundamentalHistoryRepository
                .findTop8ByInstrumentIdOrderByPeriodDesc(instrument.getId())
                .stream()
                .map(h -> FundamentalHistoryPoint.builder()
                        .period(h.getPeriod())
                        .revenue(h.getRevenue())
                        .netIncome(h.getNetIncome())
                        .grossMargin(h.getGrossMargin())
                        .netMargin(h.getNetMargin())
                        .roe(h.getRoe())
                        .peRatio(h.getPeRatio())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialDataResponse> getFinancialData(String instrumentCode, String periodType) {
        MarketInstrument instrument = resolveInstrument(instrumentCode);
        return companyFinancialsRepository
                .findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrument.getId(), periodType.toUpperCase())
                .stream()
                .map(f -> FinancialDataResponse.builder()
                        .id(f.getId())
                        .period(f.getPeriod())
                        .periodType(f.getPeriodType())
                        .revenue(f.getRevenue())
                        .grossProfit(f.getGrossProfit())
                        .netIncome(f.getNetIncome())
                        .totalAssets(f.getTotalAssets())
                        .totalEquity(f.getTotalEquity())
                        .totalLiabilities(f.getTotalLiabilities())
                        .currentAssets(f.getCurrentAssets())
                        .currentLiabilities(f.getCurrentLiabilities())
                        .operatingCashFlow(f.getOperatingCashFlow())
                        .build())
                .toList();
    }

    @Transactional
    public FundamentalRatiosResponse calculateLatestAnnualRatios(String instrumentCode) {
        MarketInstrument instrument = resolveInstrument(instrumentCode);
        CompanyFinancials latest = companyFinancialsRepository
                .findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrument.getId(), "ANNUAL")
                .orElseThrow(() -> new ResourceNotFoundException("ANNUAL finansal veri bulunamadı: " + instrumentCode));

        FundamentalRatios ratios = calculateRatios(instrument.getId(), latest.getPeriod());
        return toRatiosResponse(ratios, true);
    }

    @Transactional
    public FundamentalRatios calculateRatios(Long instrumentId, String period) {
        logger.info("Temel analiz oranları hesaplanıyor: instrumentId={}, period={}", instrumentId, period);

        MarketInstrument instrument = marketInstrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: id=" + instrumentId));

        CompanyFinancials financials = companyFinancialsRepository
                .findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrumentId, "ANNUAL")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Finansal veri bulunamadı: instrumentId=" + instrumentId + ", period=" + period));

        BigDecimal currentPrice = resolveCurrentPrice(instrument);
        Optional<CompanyProfile> profileOpt = companyProfileRepository
                .findByTickerCodeIgnoreCase(instrument.getInstrumentCode());

        BigDecimal sharesOutstanding = profileOpt.map(CompanyProfile::getSharesOutstanding).orElse(null);

        FundamentalRatios ratios = FundamentalRatios.builder()
                .instrument(instrument)
                .period(period != null ? period : financials.getPeriod())
                .calculationPrice(currentPrice)
                .build();

        // Temel oranlar
        ratios.setGrossMargin(pct(financials.getGrossProfit(), financials.getRevenue()));
        ratios.setNetMargin(pct(financials.getNetIncome(), financials.getRevenue()));
        ratios.setRoe(pct(financials.getNetIncome(), financials.getTotalEquity()));
        ratios.setRoa(pct(financials.getNetIncome(), financials.getTotalAssets()));
        ratios.setDebtToEquity(ratio(financials.getTotalLiabilities(), financials.getTotalEquity()));
        ratios.setCurrentRatio(ratio(financials.getCurrentAssets(), financials.getCurrentLiabilities()));

        // Değerleme oranları
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

                // Graham Sayısı = sqrt(22.5 × EPS × BVPS)
                BigDecimal grahamSquared = BigDecimal.valueOf(22.5)
                        .multiply(eps.max(BigDecimal.ZERO))
                        .multiply(bookValuePerShare.max(BigDecimal.ZERO));
                if (grahamSquared.compareTo(BigDecimal.ZERO) > 0) {
                    ratios.setGrahamNumber(BigDecimal.valueOf(Math.sqrt(grahamSquared.doubleValue()))
                            .setScale(SCALE, RoundingMode.HALF_UP));
                }

                // Altman Z-Score
                if (financials.getTotalAssets() != null && financials.getTotalAssets().compareTo(BigDecimal.ZERO) > 0
                        && financials.getTotalLiabilities() != null) {
                    BigDecimal totalAssets = financials.getTotalAssets();
                    BigDecimal workingCapital = safeSubtract(financials.getCurrentAssets(), financials.getCurrentLiabilities());
                    BigDecimal x1 = workingCapital != null ? workingCapital.divide(totalAssets, SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    // X2 (retained earnings) — mevcut şemada yok, 0 olarak alınıyor
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

        // YoY büyüme — önceki yıl verisi
        List<CompanyFinancials> history = companyFinancialsRepository
                .findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrumentId, "ANNUAL");
        if (history.size() >= 2) {
            CompanyFinancials prev = history.get(1);
            ratios.setRevenueGrowthYoy(yoyGrowth(financials.getRevenue(), prev.getRevenue()));
            ratios.setNetIncomeGrowthYoy(yoyGrowth(financials.getNetIncome(), prev.getNetIncome()));
            ratios.setAssetGrowthYoy(yoyGrowth(financials.getTotalAssets(), prev.getTotalAssets()));
            ratios.setPiotroskiScore(calculatePiotroski(financials, prev));
        }

        ratios.setOverallSignal(determineSignal(ratios));

        FundamentalRatios saved = fundamentalRatiosRepository.save(ratios);
        syncFundamentalHistory(instrument, financials, saved);

        logger.info("Temel analiz oranları kaydedildi: instrumentId={}, period={}, signal={}",
                instrumentId, period, saved.getOverallSignal());
        return saved;
    }

    private int calculatePiotroski(CompanyFinancials current, CompanyFinancials prev) {
        int score = 0;
        // F1: ROA > 0
        if (isPositive(current.getNetIncome()) && isPositive(current.getTotalAssets())) score++;
        // F2: Operating Cash Flow > 0
        if (isPositive(current.getOperatingCashFlow())) score++;
        // F3: ROA arttı
        double roa = safeRatio(current.getNetIncome(), current.getTotalAssets());
        double prevRoa = safeRatio(prev.getNetIncome(), prev.getTotalAssets());
        if (roa > prevRoa) score++;
        // F4: Cash Flow > Net Income (Accrual)
        if (current.getOperatingCashFlow() != null && current.getNetIncome() != null
                && current.getOperatingCashFlow().compareTo(current.getNetIncome()) > 0) score++;
        // F5: Uzun vadeli borç azaldı (Total Liabilities proxy)
        if (current.getTotalLiabilities() != null && prev.getTotalLiabilities() != null
                && current.getTotalLiabilities().compareTo(prev.getTotalLiabilities()) < 0) score++;
        // F6: Current Ratio arttı
        double cr = safeRatio(current.getCurrentAssets(), current.getCurrentLiabilities());
        double prevCr = safeRatio(prev.getCurrentAssets(), prev.getCurrentLiabilities());
        if (cr > prevCr) score++;
        // F7: Yeni hisse ihracı yok — şemada bilgi yok, default 1 puan
        score++;
        // F8: Gross Margin arttı
        double gm = safeRatio(current.getGrossProfit(), current.getRevenue());
        double prevGm = safeRatio(prev.getGrossProfit(), prev.getRevenue());
        if (gm > prevGm) score++;
        // F9: Asset Turnover arttı
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

    private void syncFundamentalHistory(MarketInstrument instrument, CompanyFinancials financials, FundamentalRatios ratios) {
        FundamentalHistory history = FundamentalHistory.builder()
                .instrument(instrument)
                .period(financials.getPeriod())
                .revenue(financials.getRevenue())
                .netIncome(financials.getNetIncome())
                .grossMargin(ratios.getGrossMargin())
                .netMargin(ratios.getNetMargin())
                .roe(ratios.getRoe())
                .peRatio(ratios.getPeRatio())
                .build();
        fundamentalHistoryRepository.save(history);
    }

    private MarketInstrument resolveInstrument(String instrumentCode) {
        return marketInstrumentRepository
                .findByInstrumentCodeIgnoreCase(instrumentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Enstrüman bulunamadı: " + instrumentCode));
    }

    private FundamentalRatiosResponse toRatiosResponse(FundamentalRatios ratios, boolean premium) {
        return FundamentalRatiosResponse.builder()
                .period(ratios.getPeriod())
                .calculationPrice(ratios.getCalculationPrice())
                .peRatio(ratios.getPeRatio())
                .pbRatio(ratios.getPbRatio())
                .grossMargin(ratios.getGrossMargin())
                .netMargin(ratios.getNetMargin())
                .roe(ratios.getRoe())
                .roa(ratios.getRoa())
                .revenueGrowthYoy(ratios.getRevenueGrowthYoy())
                .netIncomeGrowthYoy(ratios.getNetIncomeGrowthYoy())
                .assetGrowthYoy(ratios.getAssetGrowthYoy())
                .debtToEquity(ratios.getDebtToEquity())
                .currentRatio(ratios.getCurrentRatio())
                .overallSignal(ratios.getOverallSignal())
                .grahamNumber(premium ? ratios.getGrahamNumber() : null)
                .piotroskiScore(premium ? ratios.getPiotroskiScore() : null)
                .altmanZScore(premium ? ratios.getAltmanZScore() : null)
                .premiumRequired(!premium)
                .calculatedAt(ratios.getCalculatedAt())
                .build();
    }

    private BigDecimal resolveCurrentPrice(MarketInstrument instrument) {
        return marketPriceHistoryRepository
                .findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY)
                .map(MarketPriceHistory::getClosePrice)
                .orElse(BigDecimal.ONE);
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
