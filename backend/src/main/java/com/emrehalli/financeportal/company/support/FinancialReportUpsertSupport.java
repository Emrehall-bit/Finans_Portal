package com.emrehalli.financeportal.company.support;

import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialValueRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class FinancialReportUpsertSupport {

    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;

    public FinancialReportUpsertSupport(CompanyFinancialReportRepository reportRepository,
                                        CompanyFinancialValueRepository valueRepository) {
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
    }

    public ReportUpsertResult upsertReport(CompanyProfile company,
                                           Integer periodYear,
                                           Integer periodQuarter,
                                           ReportType reportType,
                                           String sourceUrl,
                                           LocalDate publishedAt,
                                           ParseStatus parseStatus,
                                           OffsetDateTime lastCheckedAt) {
        Optional<CompanyFinancialReport> existing = reportRepository
                .findByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(
                        company.getId(), periodYear, periodQuarter, reportType);

        CompanyFinancialReport report = existing.orElseGet(() -> CompanyFinancialReport.builder()
                .company(company)
                .periodYear(periodYear)
                .periodQuarter(periodQuarter)
                .reportType(reportType)
                .createdAt(OffsetDateTime.now())
                .build());

        report.setSourceUrl(sourceUrl);
        report.setPublishedAt(publishedAt);
        report.setParseStatus(parseStatus);
        report.setLastCheckedAt(lastCheckedAt);

        return new ReportUpsertResult(reportRepository.save(report), existing.isEmpty());
    }

    public ValueUpsertResult upsertValue(CompanyFinancialReport report,
                                         String itemKey,
                                         String rawLabel,
                                         BigDecimal value,
                                         String currency,
                                         Integer unitMultiplier,
                                         boolean currentPeriod) {
        Optional<CompanyFinancialValue> existing = valueRepository.findByReportIdAndItemKey(report.getId(), itemKey);
        CompanyFinancialValue financialValue = existing.orElseGet(() -> CompanyFinancialValue.builder()
                .report(report)
                .itemKey(itemKey)
                .createdAt(OffsetDateTime.now())
                .build());

        financialValue.setRawLabel(rawLabel);
        financialValue.setValue(value);
        financialValue.setCurrency(currency);
        financialValue.setUnitMultiplier(unitMultiplier);
        financialValue.setCurrentPeriod(currentPeriod);
        valueRepository.save(financialValue);

        return new ValueUpsertResult(financialValue, existing.isPresent());
    }

    public int deleteMissingValues(Long reportId, Set<String> keepItemKeys) {
        List<CompanyFinancialValue> staleValues = valueRepository.findByReportId(reportId).stream()
                .filter(value -> !keepItemKeys.contains(value.getItemKey()))
                .toList();
        if (!staleValues.isEmpty()) {
            valueRepository.deleteAll(staleValues);
        }
        return staleValues.size();
    }

    public record ReportUpsertResult(CompanyFinancialReport report, boolean created) {
    }

    public record ValueUpsertResult(CompanyFinancialValue value, boolean updated) {
    }
}


