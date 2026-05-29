package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.response.CompanyRatioCalculationResponse;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.entity.CompanyRatio;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.FinancialItemKey;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialValueRepository;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.company.persistence.CompanyRatioRepository;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CompanyRatioService {

    private static final Logger logger = LogManager.getLogger(CompanyRatioService.class);
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final List<ParseStatus> ELIGIBLE_STATUSES = List.of(ParseStatus.SUCCESS);
    private static final String STATUS_CALCULATED = "CALCULATED";
    private static final String PE_STATUS_POSITIVE_EARNINGS = "POSITIVE_EARNINGS";
    private static final String PE_STATUS_NEGATIVE_EARNINGS = "NEGATIVE_EARNINGS";
    private static final String PE_STATUS_ZERO_OR_MISSING_EARNINGS = "ZERO_OR_MISSING_EARNINGS";
    private static final String STATUS_MISSING_PRICE = "MISSING_PRICE";
    private static final String STATUS_MISSING_SHARES_OUTSTANDING = "MISSING_SHARES_OUTSTANDING";
    private static final String STATUS_MISSING_NET_PROFIT = "MISSING_NET_PROFIT";
    private static final String STATUS_MISSING_EQUITY = "MISSING_EQUITY";
    private static final String STATUS_ZERO_EQUITY = "ZERO_EQUITY";

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioRepository ratioRepository;
    private final MarketQueryService marketQueryService;
    private final FinancialQuarterNormalizer quarterNormalizer;

    public CompanyRatioService(CompanyProfileRepository profileRepository,
                               CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               CompanyRatioRepository ratioRepository,
                               MarketQueryService marketQueryService,
                               FinancialQuarterNormalizer quarterNormalizer) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioRepository = ratioRepository;
        this.marketQueryService = marketQueryService;
        this.quarterNormalizer = quarterNormalizer;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public CompanyRatioCalculationResponse calculateForTicker(String ticker) {
        CompanyProfile company = requireCompany(ticker);

        List<CompanyFinancialReport> eligible = reportRepository.findEligibleReports(ticker, ELIGIBLE_STATUSES);
        if (eligible.isEmpty()) {
            return failed(ticker, "Hesaplanabilir finansal rapor bulunamadÄ± (SUCCESS).");
        }

        CompanyFinancialReport latest = eligible.get(0);

        Optional<MarketQueryService.MarketSnapshot> snapshot = marketQueryService.findBySymbol(ticker);
        if (snapshot.isEmpty() || snapshot.get().price() == null) {
            return failed(ticker, "GÃ¼ncel fiyat bulunamadÄ±: " + ticker);
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
        BigDecimal toplamKaynaklar    = get(values, FinancialItemKey.TOPLAM_KAYNAKLAR);
        BigDecimal toplamYukumlulukler= get(values, FinancialItemKey.TOPLAM_YUKUMLULUKLER);
        BigDecimal kisaVadeliYukumlulukler = get(values, FinancialItemKey.KISA_VADELI_YUKUMLULUKLER);
        BigDecimal uzunVadeliYukumlulukler = get(values, FinancialItemKey.UZUN_VADELI_YUKUMLULUKLER);
        BigDecimal toplamBorc = sum(kisaVadeliYukumlulukler, uzunVadeliYukumlulukler);
        BigDecimal quarterlyHasilat = quarterNormalizer.getQuarterlyValue(company.getId(), FinancialItemKey.HASILAT, report.getPeriodYear(), report.getPeriodQuarter());
        BigDecimal quarterlyBrutKar = quarterNormalizer.getQuarterlyValue(company.getId(), FinancialItemKey.BRUT_KAR, report.getPeriodYear(), report.getPeriodQuarter());
        BigDecimal quarterlyNetDonemKari = quarterNormalizer.getQuarterlyValue(company.getId(), FinancialItemKey.NET_DONEM_KARI, report.getPeriodYear(), report.getPeriodQuarter());
        BigDecimal ttmNetDonemKari = quarterNormalizer.getTtmValue(company.getId(), FinancialItemKey.NET_DONEM_KARI, report.getPeriodYear(), report.getPeriodQuarter());

        BigDecimal marketCap  = multiply(price, company.getSharesOutstanding());
        BigDecimal peRatio    = computePeRatio(marketCap, ttmNetDonemKari);
        BigDecimal pbRatio    = divide(marketCap, ozkaynaklar);
        BigDecimal debtToEq   = divide(toplamBorc != null ? toplamBorc : toplamYukumlulukler, ozkaynaklar);
        BigDecimal grossMargin= divide(quarterlyBrutKar, quarterlyHasilat);
        BigDecimal netMargin  = divide(quarterlyNetDonemKari, quarterlyHasilat);
        BigDecimal roe        = divide(netDonemKari, ozkaynaklar);
        BigDecimal roa        = divide(netDonemKari, toplamVarliklar);

        BigDecimal revGrowth     = null;
        BigDecimal netProfGrowth = null;
        BigDecimal assetGrowth   = null;
        BigDecimal prevYearQuarterlyHasilat = quarterNormalizer.getQuarterlyValue(company.getId(), FinancialItemKey.HASILAT, safeSubtract(report.getPeriodYear(), 1), report.getPeriodQuarter());
        BigDecimal prevYearQuarterlyNetProfit = quarterNormalizer.getQuarterlyValue(company.getId(), FinancialItemKey.NET_DONEM_KARI, safeSubtract(report.getPeriodYear(), 1), report.getPeriodQuarter());
        GrowthResult netProfitGrowth = netProfitGrowthResult(quarterlyNetDonemKari, prevYearQuarterlyNetProfit);
        revGrowth = growth(quarterlyHasilat, prevYearQuarterlyHasilat);
        netProfGrowth = netProfitGrowth.value();
        String revenueGrowthLabel = null;
        String netProfitGrowthLabel = netProfitGrowth.label();
        String assetGrowthLabel = null;
        if (!prevValues.isEmpty()) {
            assetGrowth = growth(toplamVarliklar, get(prevValues, FinancialItemKey.TOPLAM_VARLIKLAR));
        }

        logger.info("Ratio asset metrics. ticker={}, reportId={}, currentAssets={}, totalResources={}, previousAssets={}, calculatedRoa={}, calculatedAssetGrowth={}",
                company.getTickerCode(),
                report.getId(),
                toplamVarliklar,
                toplamKaynaklar,
                get(prevValues, FinancialItemKey.TOPLAM_VARLIKLAR),
                roa,
                assetGrowth);
        logger.info("Ratio valuation metrics. ticker={}, reportId={}, price={}, sharesOutstanding={}, marketCap={}, netProfit={}, ttmNetProfit={}, equity={}, shortTermLiabilities={}, longTermLiabilities={}, totalDebt={}, grossProfit={}, revenue={}, quarterlyGrossProfit={}, quarterlyRevenue={}, peRatio={}, pbRatio={}, debtToEquity={}, grossMargin={}, revenueGrowth={}, netProfitGrowth={}",
                company.getTickerCode(),
                report.getId(),
                price,
                company.getSharesOutstanding(),
                marketCap,
                netDonemKari,
                ttmNetDonemKari,
                ozkaynaklar,
                kisaVadeliYukumlulukler,
                uzunVadeliYukumlulukler,
                toplamBorc,
                brutKar,
                hasilat,
                quarterlyBrutKar,
                quarterlyHasilat,
                peRatio,
                pbRatio,
                debtToEq,
                grossMargin,
                revGrowth,
                netProfGrowth);

        RatioFieldStatus peFieldStatus = resolvePeStatus(price, company.getSharesOutstanding(), ttmNetDonemKari, peRatio);
        RatioFieldStatus pbFieldStatus = resolvePbStatus(price, company.getSharesOutstanding(), ozkaynaklar, pbRatio);
        String missingReason = combineMissingReasons(peFieldStatus, pbFieldStatus);
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
        ratio.setRevenueGrowthLabel(revenueGrowthLabel);
        ratio.setNetProfitGrowth(netProfGrowth);
        ratio.setNetProfitGrowthLabel(netProfitGrowthLabel);
        ratio.setAssetGrowth(assetGrowth);
        ratio.setAssetGrowthLabel(assetGrowthLabel);
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
                .missingReason(missingReason)
                .peStatus(peFieldStatus.status())
                .peMissingReason(peFieldStatus.reason())
                .pbStatus(pbFieldStatus.status())
                .pbMissingReason(pbFieldStatus.reason())
                .peRatio(peRatio)
                .pbRatio(pbRatio)
                .debtToEquity(debtToEq)
                .grossMargin(grossMargin)
                .netMargin(netMargin)
                .roe(roe)
                .roa(roa)
                .revenueGrowth(revGrowth)
                .revenueGrowthLabel(revenueGrowthLabel)
                .netProfitGrowth(netProfGrowth)
                .netProfitGrowthLabel(netProfitGrowthLabel)
                .assetGrowth(assetGrowth)
                .assetGrowthLabel(assetGrowthLabel)
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

    BigDecimal sum(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    BigDecimal computePeRatio(BigDecimal marketCap, BigDecimal netProfit) {
        if (netProfit == null || netProfit.compareTo(BigDecimal.ZERO) == 0) return null;
        return divide(marketCap, netProfit);
    }

    private RatioFieldStatus resolvePeStatus(BigDecimal price,
                                             BigDecimal sharesOutstanding,
                                             BigDecimal ttmNetProfit,
                                             BigDecimal peRatio) {
        if (peRatio != null) {
            if (peRatio.compareTo(BigDecimal.ZERO) < 0) {
                return new RatioFieldStatus(PE_STATUS_NEGATIVE_EARNINGS, "negative earnings");
            }
            return new RatioFieldStatus(PE_STATUS_POSITIVE_EARNINGS, null);
        }
        if (price == null) {
            return new RatioFieldStatus(STATUS_MISSING_PRICE, "missing price");
        }
        if (sharesOutstanding == null || sharesOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return new RatioFieldStatus(STATUS_MISSING_SHARES_OUTSTANDING, "missing shares outstanding");
        }
        if (ttmNetProfit == null) {
            return new RatioFieldStatus(PE_STATUS_ZERO_OR_MISSING_EARNINGS, "missing net profit");
        }
        if (ttmNetProfit.compareTo(BigDecimal.ZERO) == 0) {
            return new RatioFieldStatus(PE_STATUS_ZERO_OR_MISSING_EARNINGS, "zero or missing earnings");
        }
        return new RatioFieldStatus(PE_STATUS_ZERO_OR_MISSING_EARNINGS, "unable to calculate pe ratio");
    }

    private RatioFieldStatus resolvePbStatus(BigDecimal price,
                                             BigDecimal sharesOutstanding,
                                             BigDecimal equity,
                                             BigDecimal pbRatio) {
        if (pbRatio != null) {
            return new RatioFieldStatus(STATUS_CALCULATED, null);
        }
        if (price == null) {
            return new RatioFieldStatus(STATUS_MISSING_PRICE, "missing price");
        }
        if (sharesOutstanding == null || sharesOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return new RatioFieldStatus(STATUS_MISSING_SHARES_OUTSTANDING, "missing shares outstanding");
        }
        if (equity == null) {
            return new RatioFieldStatus(STATUS_MISSING_EQUITY, "missing equity");
        }
        if (equity.compareTo(BigDecimal.ZERO) == 0) {
            return new RatioFieldStatus(STATUS_ZERO_EQUITY, "zero equity");
        }
        return new RatioFieldStatus(STATUS_MISSING_EQUITY, "unable to calculate pb ratio");
    }

    private String combineMissingReasons(RatioFieldStatus peStatus, RatioFieldStatus pbStatus) {
        List<String> reasons = new ArrayList<>();
        if (peStatus != null && peStatus.reason() != null && !peStatus.reason().isBlank()) {
            reasons.add("pe: " + peStatus.reason());
        }
        if (pbStatus != null && pbStatus.reason() != null && !pbStatus.reason().isBlank()) {
            reasons.add("pb: " + pbStatus.reason());
        }
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }

    BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null
                || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.divide(previous, SCALE, ROUNDING).subtract(BigDecimal.ONE);
    }

    GrowthResult netProfitGrowthResult(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null
                || previous.compareTo(BigDecimal.ZERO) <= 0) {
            if (current != null && previous != null
                    && previous.compareTo(BigDecimal.ZERO) <= 0
                    && current.compareTo(BigDecimal.ZERO) > 0) {
                return new GrowthResult(null, "KÃ¢rlÄ±lÄ±ÄŸa GeÃ§ti");
            }
            return new GrowthResult(null, null);
        }
        if (current.compareTo(BigDecimal.ZERO) < 0) {
            return new GrowthResult(null, "Zarara GeÃ§ti");
        }
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            return new GrowthResult(null, null);
        }
        return new GrowthResult(current.divide(previous, SCALE, ROUNDING).subtract(BigDecimal.ONE), null);
    }

    private Integer safeSubtract(Integer value, int amount) {
        return value != null ? value - amount : null;
    }

    String computeHealthLabel(BigDecimal debtToEquity, BigDecimal netMargin, BigDecimal roe) {
        BigDecimal debtThreshold   = new BigDecimal("2");
        BigDecimal marginThreshold = new BigDecimal("0.15");

        if (debtToEquity != null && debtToEquity.compareTo(debtThreshold) > 0) {
            return "BorÃ§luluk yÃ¼ksek";
        }
        if (netMargin != null && netMargin.compareTo(marginThreshold) > 0
                && roe != null && roe.compareTo(marginThreshold) > 0) {
            return "KÃ¢rlÄ±lÄ±k gÃ¼Ã§lÃ¼";
        }
        if (netMargin != null && netMargin.compareTo(BigDecimal.ZERO) < 0) {
            return "Zarar aÃ§Ä±klamÄ±ÅŸ";
        }
        return "NÃ¶tr";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, BigDecimal> loadValueMap(Long reportId) {
        Map<String, BigDecimal> result = new java.util.HashMap<>();
        for (CompanyFinancialValue v : valueRepository.findByReportId(reportId)) {
            if (v.getValue() == null) continue;
            BigDecimal scaled = scaledValue(v);
            if (scaled != null) result.putIfAbsent(v.getItemKey(), scaled);
        }
        return result;
    }

    private BigDecimal scaledValue(CompanyFinancialValue value) {
        return quarterNormalizer.effectiveValue(value);
    }

    private BigDecimal get(Map<String, BigDecimal> map, FinancialItemKey key) {
        return map.get(key.name());
    }

    private CompanyProfile requireCompany(String ticker) {
        return profileRepository.findByTickerCodeIgnoreCase(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Åirket bulunamadÄ±: " + ticker));
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

    record GrowthResult(BigDecimal value, String label) {
    }

    private record RatioFieldStatus(String status, String reason) {
    }
}




