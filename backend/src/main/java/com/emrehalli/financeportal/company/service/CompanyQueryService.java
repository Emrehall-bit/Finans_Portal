package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.*;
import com.emrehalli.financeportal.company.entity.*;
import com.emrehalli.financeportal.company.enums.ReportType;
import com.emrehalli.financeportal.company.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CompanyQueryService {

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioRepository ratioRepository;
    private final CompanyDisclosureRepository disclosureRepository;
    private final FinancialQuarterNormalizer quarterNormalizer;

    public CompanyQueryService(CompanyProfileRepository profileRepository,
                               CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               CompanyRatioRepository ratioRepository,
                               CompanyDisclosureRepository disclosureRepository,
                               FinancialQuarterNormalizer quarterNormalizer) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioRepository = ratioRepository;
        this.disclosureRepository = disclosureRepository;
        this.quarterNormalizer = quarterNormalizer;
    }

    public List<CompanyProfileResponse> listActiveCompanies() {
        return profileRepository.findByActiveTrue().stream()
                .map(this::toProfileResponse)
                .toList();
    }

    public CompanyProfileResponse getCompany(String ticker) {
        return toProfileResponse(requireCompany(ticker));
    }

    public List<CompanyFinancialReportResponse> getFinancials(String ticker) {
        requireCompany(ticker);
        List<CompanyFinancialReport> reports = reportRepository
                .findByCompanyTickerCodeIgnoreCaseOrderByPeriodYearDescPeriodQuarterDesc(ticker);
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> reportIds = reports.stream().map(CompanyFinancialReport::getId).toList();
        Map<Long, List<CompanyFinancialValue>> valuesByReportId = valueRepository
                .findByReportIdIn(reportIds)
                .stream()
                .collect(Collectors.groupingBy(v -> v.getReport().getId()));
        Map<String, CompanyFinancialValue> valueByPeriodAndItem = buildValueComparisonIndex(reports, valuesByReportId);
        Map<Long, CompanyFinancialReport> reportById = reports.stream()
                .collect(Collectors.toMap(CompanyFinancialReport::getId, report -> report));
        return reports.stream()
                .map(r -> toReportResponse(
                        r,
                        valuesByReportId.getOrDefault(r.getId(), List.of()),
                        valueByPeriodAndItem,
                        reportById))
                .toList();
    }

    public Page<CompanyDisclosureResponse> getDisclosures(String ticker, Pageable pageable) {
        requireCompany(ticker);
        return disclosureRepository
                .findByCompanyTickerCodeIgnoreCaseOrderByPublishedAtDesc(ticker, pageable)
                .map(this::toDisclosureResponse);
    }

    public CompanyFundamentalsResponse getFundamentals(String ticker) {
        CompanyProfile company = requireCompany(ticker);
        CompanyRatio ratio = ratioRepository
                .findTopByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(ticker)
                .orElse(null);
        return toFundamentalsResponse(company, ratio);
    }

    private CompanyProfile requireCompany(String ticker) {
        return profileRepository.findByTickerCodeIgnoreCase(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + ticker));
    }

    private CompanyProfileResponse toProfileResponse(CompanyProfile c) {
        return CompanyProfileResponse.builder()
                .tickerCode(c.getTickerCode())
                .companyName(c.getCompanyName())
                .sector(c.getSector())
                .market(c.getMarket())
                .kapCompanyId(c.getKapCompanyId())
                .mkkMemberOid(c.getMkkMemberOid())
                .sharesOutstanding(c.getSharesOutstanding())
                .active(c.isActive())
                .build();
    }

    private CompanyFinancialReportResponse toReportResponse(CompanyFinancialReport r,
                                                            List<CompanyFinancialValue> values,
                                                            Map<String, CompanyFinancialValue> valueByPeriodAndItem,
                                                            Map<Long, CompanyFinancialReport> reportById) {
        return CompanyFinancialReportResponse.builder()
                .reportId(r.getId())
                .periodYear(r.getPeriodYear())
                .periodQuarter(r.getPeriodQuarter())
                .reportType(r.getReportType())
                .publishedAt(r.getPublishedAt())
                .parseStatus(r.getParseStatus())
                .sourceUrl(r.getSourceUrl())
                .lastCheckedAt(r.getLastCheckedAt())
                .values(values.stream().map(v -> toValueItem(v, r, valueByPeriodAndItem, reportById)).toList())
                .build();
    }

    private FinancialValueItemResponse toValueItem(CompanyFinancialValue v,
                                                   CompanyFinancialReport report,
                                                   Map<String, CompanyFinancialValue> valueByPeriodAndItem,
                                                   Map<Long, CompanyFinancialReport> reportById) {
        FinancialValueItemResponse.FinancialValueItemResponseBuilder builder = FinancialValueItemResponse.builder()
                .itemKey(v.getItemKey())
                .rawLabel(v.getRawLabel())
                .value(v.getValue())
                .currency(v.getCurrency())
                .unitMultiplier(v.getUnitMultiplier())
                .currentPeriod(v.isCurrentPeriod());

        CompanyFinancialValue comparisonValue = valueByPeriodAndItem.get(comparisonValueKey(report, v.getItemKey()));
        BigDecimal changePercent = calculateChangePercent(v, comparisonValue);
        if (comparisonValue != null && changePercent != null) {
            CompanyFinancialReport comparisonReport = reportById.get(comparisonValue.getReport().getId());
            builder.comparisonAvailable(true)
                    .comparisonPeriod(formatPeriod(comparisonReport))
                    .changePercent(changePercent);
        } else {
            builder.comparisonAvailable(false);
        }
        return builder.build();
    }

    private Map<String, CompanyFinancialValue> buildValueComparisonIndex(List<CompanyFinancialReport> reports,
                                                                         Map<Long, List<CompanyFinancialValue>> valuesByReportId) {
        Map<String, CompanyFinancialValue> index = new HashMap<>();
        for (CompanyFinancialReport report : reports) {
            for (CompanyFinancialValue value : valuesByReportId.getOrDefault(report.getId(), List.of())) {
                index.put(valueKey(report, value.getItemKey()), value);
            }
        }
        return index;
    }

    private String comparisonValueKey(CompanyFinancialReport report, String itemKey) {
        Integer year = report.getPeriodYear() != null ? report.getPeriodYear() - 1 : null;
        return valueKey(year, periodToken(report), itemKey);
    }

    private String valueKey(CompanyFinancialReport report, String itemKey) {
        return valueKey(report.getPeriodYear(), periodToken(report), itemKey);
    }

    private String valueKey(Integer year, String periodToken, String itemKey) {
        return year + ":" + periodToken + ":" + itemKey;
    }

    private String periodToken(CompanyFinancialReport report) {
        if (report == null) return "UNKNOWN";
        if (report.getReportType() != null) {
            return report.getReportType().name();
        }
        return report.getPeriodQuarter() != null ? "Q" + report.getPeriodQuarter() : "UNKNOWN";
    }

    private BigDecimal calculateChangePercent(CompanyFinancialValue current, CompanyFinancialValue previous) {
        if (current == null || previous == null) return null;
        BigDecimal currentValue = quarterNormalizer.effectiveValue(current);
        BigDecimal previousValue = quarterNormalizer.effectiveValue(previous);
        if (currentValue == null || previousValue == null || previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentValue.subtract(previousValue)
                .divide(previousValue.abs(), 6, RoundingMode.HALF_UP);
    }

    private CompanyDisclosureResponse toDisclosureResponse(CompanyDisclosure d) {
        return CompanyDisclosureResponse.builder()
                .id(d.getId())
                .disclosureType(d.getDisclosureType())
                .title(d.getTitle())
                .kapUrl(d.getKapUrl())
                .publishedAt(d.getPublishedAt())
                .summary(d.getSummary())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private CompanyFundamentalsResponse toFundamentalsResponse(CompanyProfile company, CompanyRatio ratio) {
        CompanyFundamentalsResponse.CompanyFundamentalsResponseBuilder builder = CompanyFundamentalsResponse.builder()
                .tickerCode(company.getTickerCode())
                .companyName(company.getCompanyName())
                .sector(company.getSector())
                .market(company.getMarket());

        if (ratio == null) {
            return builder.message("Temel analiz verisi henüz hesaplanmadı.").build();
        }

        CompanyFinancialReport report = ratio.getReport();
        return builder
                .latestReportPeriod(formatPeriod(report))
                .latestReportType(report != null ? report.getReportType() : null)
                .latestReportPublishedAt(report != null ? report.getPublishedAt() : null)
                .parseStatus(report != null ? report.getParseStatus() : null)
                .priceAtCalc(ratio.getPriceAtCalc())
                .calculatedAt(ratio.getCalculatedAt())
                .marketCap(ratio.getMarketCap())
                .peRatio(ratio.getPeRatio())
                .pbRatio(ratio.getPbRatio())
                .debtToEquity(ratio.getDebtToEquity())
                .grossMargin(ratio.getGrossMargin())
                .netMargin(ratio.getNetMargin())
                .roe(ratio.getRoe())
                .roa(ratio.getRoa())
                .revenueGrowth(ratio.getRevenueGrowth())
                .revenueGrowthLabel(ratio.getRevenueGrowthLabel())
                .netProfitGrowth(ratio.getNetProfitGrowth())
                .netProfitGrowthLabel(ratio.getNetProfitGrowthLabel())
                .assetGrowth(ratio.getAssetGrowth())
                .assetGrowthLabel(ratio.getAssetGrowthLabel())
                .healthLabel(ratio.getHealthLabel())
                .build();
    }

    private String formatPeriod(CompanyFinancialReport report) {
        if (report == null || report.getPeriodYear() == null || report.getReportType() == null) return null;
        return switch (report.getReportType()) {
            case ANNUAL -> report.getPeriodYear() + "/ANNUAL";
            case Q1     -> report.getPeriodYear() + "/Q1";
            case Q2     -> report.getPeriodYear() + "/Q2";
            case Q3     -> report.getPeriodYear() + "/Q3";
            case Q4     -> report.getPeriodYear() + "/Q4";
        };
    }
}
