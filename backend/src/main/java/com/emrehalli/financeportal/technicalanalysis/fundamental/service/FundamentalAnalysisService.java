package com.emrehalli.financeportal.technicalanalysis.fundamental.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FinancialDataResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalHistoryPoint;
import com.emrehalli.financeportal.technicalanalysis.fundamental.dto.FundamentalRatiosResponse;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.CompanyFinancials;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalHistory;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalRatios;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalRatiosRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class FundamentalAnalysisService {

    private static final Logger logger = LogManager.getLogger(FundamentalAnalysisService.class);
    private static final String ANNUAL = "ANNUAL";

    private final CompanyFinancialsRepository companyFinancialsRepository;
    private final FundamentalRatiosRepository fundamentalRatiosRepository;
    private final FundamentalHistoryRepository fundamentalHistoryRepository;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final FundamentalRatioCalculator fundamentalRatioCalculator;

    public FundamentalAnalysisService(CompanyFinancialsRepository companyFinancialsRepository,
                                      FundamentalRatiosRepository fundamentalRatiosRepository,
                                      FundamentalHistoryRepository fundamentalHistoryRepository,
                                      MarketInstrumentRepository marketInstrumentRepository,
                                      MarketPriceHistoryRepository marketPriceHistoryRepository,
                                      CompanyProfileRepository companyProfileRepository,
                                      FundamentalRatioCalculator fundamentalRatioCalculator) {
        this.companyFinancialsRepository = companyFinancialsRepository;
        this.fundamentalRatiosRepository = fundamentalRatiosRepository;
        this.fundamentalHistoryRepository = fundamentalHistoryRepository;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.fundamentalRatioCalculator = fundamentalRatioCalculator;
    }

    @Transactional(readOnly = true)
    public FundamentalRatiosResponse getLatestRatios(String instrumentCode, boolean premium) {
        MarketInstrument instrument = resolveInstrument(instrumentCode);
        FundamentalRatios ratios = fundamentalRatiosRepository
                .findTopByInstrumentIdOrderByCalculatedAtDesc(instrument.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Temel analiz verisi bulunamadi: " + instrumentCode));

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
        List<CompanyFinancials> annualHistory = getAnnualFinancials(instrument.getId());
        FundamentalRatios ratios = calculateRatios(instrument, annualHistory.getFirst(), annualHistory);
        return toRatiosResponse(ratios, true);
    }

    @Transactional
    public FundamentalRatios calculateRatios(Long instrumentId, String period) {
        logger.info("Temel analiz oranlari hesaplaniyor: instrumentId={}, period={}", instrumentId, period);

        MarketInstrument instrument = marketInstrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enstruman bulunamadi: id=" + instrumentId));
        List<CompanyFinancials> annualHistory = getAnnualFinancials(instrumentId);
        CompanyFinancials financials = resolveAnnualFinancials(instrumentId, period, annualHistory);

        return calculateRatios(instrument, financials, annualHistory);
    }

    private FundamentalRatios calculateRatios(MarketInstrument instrument,
                                              CompanyFinancials financials,
                                              List<CompanyFinancials> annualHistory) {
        BigDecimal currentPrice = resolveCurrentPrice(instrument);
        Optional<CompanyProfile> profileOpt = companyProfileRepository
                .findByTickerCodeIgnoreCase(instrument.getInstrumentCode());

        BigDecimal sharesOutstanding = profileOpt.map(CompanyProfile::getSharesOutstanding).orElse(null);

        FundamentalRatios ratios = FundamentalRatios.builder()
                .instrument(instrument)
                .period(financials.getPeriod())
                .calculationPrice(currentPrice)
                .build();

        // Saf hesaplama (margin, ROE/ROA, D/E, current ratio, P/E, P/B, Graham, Altman Z, YoY,
        // Piotroski, overall signal) calculator'a delege edilir; servis veri okuma + persistence yapar.
        fundamentalRatioCalculator.populate(ratios, financials, annualHistory, currentPrice, sharesOutstanding);

        FundamentalRatios saved = fundamentalRatiosRepository.save(ratios);
        syncFundamentalHistory(instrument, financials, saved);

        logger.info("Temel analiz oranlari kaydedildi: instrumentId={}, period={}, signal={}",
                instrument.getId(), financials.getPeriod(), saved.getOverallSignal());
        return saved;
    }

    private List<CompanyFinancials> getAnnualFinancials(Long instrumentId) {
        List<CompanyFinancials> annualHistory = companyFinancialsRepository
                .findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(instrumentId, ANNUAL);
        if (annualHistory.isEmpty()) {
            throw new ResourceNotFoundException("ANNUAL finansal veri bulunamadi: instrumentId=" + instrumentId);
        }
        return annualHistory;
    }

    private CompanyFinancials resolveAnnualFinancials(Long instrumentId,
                                                      String period,
                                                      List<CompanyFinancials> annualHistory) {
        if (period == null || period.isBlank()) {
            return annualHistory.getFirst();
        }
        return annualHistory.stream()
                .filter(financials -> period.equals(financials.getPeriod()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Finansal veri bulunamadi: instrumentId=" + instrumentId + ", period=" + period));
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
                .orElseThrow(() -> new ResourceNotFoundException("Enstruman bulunamadi: " + instrumentCode));
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
}
