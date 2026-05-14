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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CompanyQueryService {

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioRepository ratioRepository;
    private final CompanyDisclosureRepository disclosureRepository;

    public CompanyQueryService(CompanyProfileRepository profileRepository,
                               CompanyFinancialReportRepository reportRepository,
                               CompanyFinancialValueRepository valueRepository,
                               CompanyRatioRepository ratioRepository,
                               CompanyDisclosureRepository disclosureRepository) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioRepository = ratioRepository;
        this.disclosureRepository = disclosureRepository;
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
        return reports.stream()
                .map(r -> toReportResponse(r, valuesByReportId.getOrDefault(r.getId(), List.of())))
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
                .active(c.isActive())
                .build();
    }

    private CompanyFinancialReportResponse toReportResponse(CompanyFinancialReport r,
                                                            List<CompanyFinancialValue> values) {
        return CompanyFinancialReportResponse.builder()
                .reportId(r.getId())
                .periodYear(r.getPeriodYear())
                .periodQuarter(r.getPeriodQuarter())
                .reportType(r.getReportType())
                .publishedAt(r.getPublishedAt())
                .parseStatus(r.getParseStatus())
                .sourceUrl(r.getSourceUrl())
                .lastCheckedAt(r.getLastCheckedAt())
                .values(values.stream().map(this::toValueItem).toList())
                .build();
    }

    private FinancialValueItemResponse toValueItem(CompanyFinancialValue v) {
        return FinancialValueItemResponse.builder()
                .itemKey(v.getItemKey())
                .rawLabel(v.getRawLabel())
                .value(v.getValue())
                .currency(v.getCurrency())
                .unitMultiplier(v.getUnitMultiplier())
                .currentPeriod(v.isCurrentPeriod())
                .build();
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
                .netProfitGrowth(ratio.getNetProfitGrowth())
                .assetGrowth(ratio.getAssetGrowth())
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
