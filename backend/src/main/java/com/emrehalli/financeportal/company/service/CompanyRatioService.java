package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.CompanyRatioCalculationResponse;
import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.entity.CompanyRatio;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.parser.FinancialItemKey;
import com.emrehalli.financeportal.company.repository.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.repository.CompanyFinancialValueRepository;
import com.emrehalli.financeportal.company.repository.CompanyProfileRepository;
import com.emrehalli.financeportal.company.repository.CompanyRatioRepository;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CompanyRatioService {

    private static final Logger logger = LogManager.getLogger(CompanyRatioService.class);
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final List<ParseStatus> ELIGIBLE_STATUSES = List.of(ParseStatus.SUCCESS, ParseStatus.PARTIAL);

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioRepository ratioRepository;
    private final MarketQueryService marketQueryService;

    public CompanyRatioService(CompanyProfileRepository profileRepository,
                               CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               CompanyRatioRepository ratioRepository,
                               MarketQueryService marketQueryService) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioRepository = ratioRepository;
        this.marketQueryService = marketQueryService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public CompanyRatioCalculationResponse calculateForTicker(String ticker) {
        CompanyProfile company = requireCompany(ticker);

        List<CompanyFinancialReport> eligible = reportRepository.findEligibleReports(ticker, ELIGIBLE_STATUSES);
        if (eligible.isEmpty()) {
            return failed(ticker, "Hesaplanabilir finansal rapor bulunamadı (SUCCESS veya PARTIAL).");
        }

        CompanyFinancialReport latest = eligible.get(0);

        Optional<MarketQueryService.MarketSnapshot> snapshot = marketQueryService.findBySymbol(ticker);
        if (snapshot.isEmpty() || snapshot.get().price() == null) {
            return failed(ticker, "Güncel fiyat bulunamadı: " + ticker);
        }
        BigDecimal price = snapshot.get().price();

        Map<String, BigDecimal> values = loadValueMap(latest.getId());
        Map<String, BigDecimal> prevValues = eligible.size() > 1
                ? loadValueMap(eligible.get(1).getId())
                : Map.of();

        return computeAndSave(company, latest, price, values, prevValues);
    }

    public List<CompanyRatioCalculationResponse> calculateForAllActiveCompanies() {
        return profileRepository.findByActiveTrue().stream()
                .map(c -> {
                    try {
                        return calculateForTicker(c.getTickerCode());
                    } catch (Exception e) {
                        logger.error("Ratio calculation failed. ticker={}", c.getTickerCode(), e);
                        return failed(c.getTickerCode(), "Hata: " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CompanyRatioCalculationResponse recalculateLatestForTicker(String ticker) {
        return calculateForTicker(ticker);
    }

    // -------------------------------------------------------------------------
    // Calculation
    // -------------------------------------------------------------------------

    CompanyRatioCalculationResponse computeAndSave(CompanyProfile company,
                                                    CompanyFinancialReport report,
                                                    BigDecimal price,
                                                    Map<String, BigDecimal> values,
                                                    Map<String, BigDecimal> prevValues) {
        BigDecimal hasilat            = get(values, FinancialItemKey.HASILAT);
        BigDecimal brutKar            = get(values, FinancialItemKey.BRUT_KAR);
        BigDecimal netDonemKari       = get(values, FinancialItemKey.NET_DONEM_KARI);
        BigDecimal ozkaynaklar        = get(values, FinancialItemKey.OZKAYNAKLAR);
        BigDecimal toplamVarliklar    = get(values, FinancialItemKey.TOPLAM_VARLIKLAR);
        BigDecimal toplamYukumlulukler= get(values, FinancialItemKey.TOPLAM_YUKUMLULUKLER);
        BigDecimal odenmisSermeye     = get(values, FinancialItemKey.ODENMIS_SERMAYE);

        BigDecimal marketCap  = multiply(price, odenmisSermeye);
        BigDecimal peRatio    = computePeRatio(marketCap, netDonemKari);
        BigDecimal pbRatio    = divide(marketCap, ozkaynaklar);
        BigDecimal debtToEq   = divide(toplamYukumlulukler, ozkaynaklar);
        BigDecimal grossMargin= divide(brutKar, hasilat);
        BigDecimal netMargin  = divide(netDonemKari, hasilat);
        BigDecimal roe        = divide(netDonemKari, ozkaynaklar);
        BigDecimal roa        = divide(netDonemKari, toplamVarliklar);

        BigDecimal revGrowth     = null;
        BigDecimal netProfGrowth = null;
        BigDecimal assetGrowth   = null;
        if (!prevValues.isEmpty()) {
            revGrowth     = growth(hasilat,         get(prevValues, FinancialItemKey.HASILAT));
            netProfGrowth = growth(netDonemKari,    get(prevValues, FinancialItemKey.NET_DONEM_KARI));
            assetGrowth   = growth(toplamVarliklar, get(prevValues, FinancialItemKey.TOPLAM_VARLIKLAR));
        }

        String healthLabel = computeHealthLabel(debtToEq, netMargin, roe);
        OffsetDateTime now = OffsetDateTime.now();

        CompanyRatio ratio = ratioRepository
                .findByCompanyIdAndReportId(company.getId(), report.getId())
                .orElseGet(() -> CompanyRatio.builder().company(company).report(report).build());

        ratio.setPriceAtCalc(price);
        ratio.setMarketCap(marketCap);
        ratio.setPeRatio(peRatio);
        ratio.setPbRatio(pbRatio);
        ratio.setDebtToEquity(debtToEq);
        ratio.setGrossMargin(grossMargin);
        ratio.setNetMargin(netMargin);
        ratio.setRoe(roe);
        ratio.setRoa(roa);
        ratio.setRevenueGrowth(revGrowth);
        ratio.setNetProfitGrowth(netProfGrowth);
        ratio.setAssetGrowth(assetGrowth);
        ratio.setHealthLabel(healthLabel);
        ratio.setCalculatedAt(now);
        ratioRepository.save(ratio);

        logger.info("Ratio saved. ticker={}, period={}, health={}", company.getTickerCode(),
                formatPeriod(report), healthLabel);

        return CompanyRatioCalculationResponse.builder()
                .tickerCode(company.getTickerCode())
                .reportPeriod(formatPeriod(report))
                .priceAtCalc(price)
                .calculated(true)
                .peRatio(peRatio)
                .pbRatio(pbRatio)
                .debtToEquity(debtToEq)
                .grossMargin(grossMargin)
                .netMargin(netMargin)
                .roe(roe)
                .roa(roa)
                .revenueGrowth(revGrowth)
                .netProfitGrowth(netProfGrowth)
                .assetGrowth(assetGrowth)
                .healthLabel(healthLabel)
                .calculatedAt(now)
                .build();
    }

    // -------------------------------------------------------------------------
    // Arithmetic helpers
    // -------------------------------------------------------------------------

    BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null
                || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, ROUNDING);
    }

    BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.multiply(b).setScale(SCALE, ROUNDING);
    }

    BigDecimal computePeRatio(BigDecimal marketCap, BigDecimal netProfit) {
        if (netProfit == null || netProfit.compareTo(BigDecimal.ZERO) <= 0) return null;
        return divide(marketCap, netProfit);
    }

    BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null
                || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.divide(previous, SCALE, ROUNDING).subtract(BigDecimal.ONE);
    }

    String computeHealthLabel(BigDecimal debtToEquity, BigDecimal netMargin, BigDecimal roe) {
        BigDecimal debtThreshold   = new BigDecimal("2");
        BigDecimal marginThreshold = new BigDecimal("0.15");

        if (debtToEquity != null && debtToEquity.compareTo(debtThreshold) > 0) {
            return "Borçluluk yüksek";
        }
        if (netMargin != null && netMargin.compareTo(marginThreshold) > 0
                && roe != null && roe.compareTo(marginThreshold) > 0) {
            return "Kârlılık güçlü";
        }
        if (netMargin != null && netMargin.compareTo(BigDecimal.ZERO) < 0) {
            return "Zarar açıklamış";
        }
        return "Nötr";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, BigDecimal> loadValueMap(Long reportId) {
        return valueRepository.findByReportId(reportId).stream()
                .filter(v -> v.getValue() != null)
                .collect(Collectors.toMap(CompanyFinancialValue::getItemKey, CompanyFinancialValue::getValue,
                        (existing, replacement) -> existing));
    }

    private BigDecimal get(Map<String, BigDecimal> map, FinancialItemKey key) {
        return map.get(key.name());
    }

    private CompanyProfile requireCompany(String ticker) {
        return profileRepository.findByTickerCodeIgnoreCase(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + ticker));
    }

    private String formatPeriod(CompanyFinancialReport report) {
        if (report.getPeriodYear() == null || report.getReportType() == null) return "Bilinmiyor";
        return switch (report.getReportType()) {
            case ANNUAL -> report.getPeriodYear() + "/ANNUAL";
            case Q1     -> report.getPeriodYear() + "/Q1";
            case Q2     -> report.getPeriodYear() + "/Q2";
            case Q3     -> report.getPeriodYear() + "/Q3";
            case Q4     -> report.getPeriodYear() + "/Q4";
        };
    }

    private CompanyRatioCalculationResponse failed(String ticker, String reason) {
        logger.warn("Ratio calculation skipped. ticker={}, reason={}", ticker, reason);
        return CompanyRatioCalculationResponse.builder()
                .tickerCode(ticker)
                .calculated(false)
                .failedReason(reason)
                .build();
    }
}
