package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.FinancialBackfillRequest;
import com.emrehalli.financeportal.company.dto.FinancialBackfillResponse;
import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.enums.ReportType;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient.ParsedFinancialItem;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialTableClient.ParsedFinancialRow;
import com.emrehalli.financeportal.company.repository.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.repository.CompanyFinancialValueRepository;
import com.emrehalli.financeportal.company.repository.CompanyProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CompanyFinancialBackfillService {

    private static final Logger logger = LogManager.getLogger(CompanyFinancialBackfillService.class);
    private static final String SOURCE_URL = "https://www.kap.org.tr/tr/api/export/compareItems";

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final KapFinancialTableClient financialTableClient;

    public CompanyFinancialBackfillService(CompanyProfileRepository profileRepository,
                                           CompanyFinancialReportRepository reportRepository,
                                           CompanyFinancialValueRepository valueRepository,
                                           KapFinancialTableClient financialTableClient) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.financialTableClient = financialTableClient;
    }

    @Transactional
    public FinancialBackfillResponse backfill(String tickerCode, FinancialBackfillRequest request) {
        CompanyProfile company = profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));
        int endYear = request != null && request.getEndYear() != null ? request.getEndYear() : OffsetDateTime.now().getYear();
        int startYear = request != null && request.getStartYear() != null ? request.getStartYear() : endYear - 5;
        if (startYear > endYear) {
            int tmp = startYear;
            startYear = endYear;
            endYear = tmp;
        }

        List<String> yearList = new ArrayList<>();
        List<Integer> processedYears = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            yearList.add(String.valueOf(year));
            processedYears.add(year);
        }

        int parsedRows = 0;
        int savedReports = 0;
        int savedValues = 0;
        int updatedValues = 0;
        List<String> processedPeriods = new ArrayList<>();

        for (int period = 1; period <= 4; period++) {
            String periodText = String.valueOf(period);
            processedPeriods.add(periodLabel(period));
            var result = financialTableClient.fetchFinancialBackfill(company, yearList, periodText);
            parsedRows += result.rows().size();
            logger.info("KAP financial backfill period parsed. ticker={}, years={}, period={}, xlsxByteSize={}, parsedRows={}",
                    tickerCode, yearList, periodText, result.xlsxByteSize(), result.rows().size());

            for (ParsedFinancialRow row : result.rows()) {
                ReportUpsert reportUpsert = upsertReport(company, row.year(), row.period());
                CompanyFinancialReport report = reportUpsert.report();
                if (reportUpsert.created()) {
                    savedReports++;
                }
                for (ParsedFinancialItem item : row.items().values()) {
                    if (upsertValue(report, item)) {
                        updatedValues++;
                    } else {
                        savedValues++;
                    }
                }
            }
        }

        logger.info("KAP financial backfill done. ticker={}, years={}, periods={}, parsedRows={}, savedReports={}, savedValues={}, updatedValues={}",
                tickerCode, processedYears, processedPeriods, parsedRows, savedReports, savedValues, updatedValues);

        return FinancialBackfillResponse.builder()
                .tickerCode(company.getTickerCode())
                .processedYears(processedYears)
                .processedPeriods(processedPeriods)
                .parsedRows(parsedRows)
                .savedReports(savedReports)
                .savedValues(savedValues)
                .updatedValues(updatedValues)
                .message("Finansal backfill tamamlandı.")
                .build();
    }

    private ReportUpsert upsertReport(CompanyProfile company, Integer year, Integer period) {
        ReportType reportType = reportType(period);
        Optional<CompanyFinancialReport> existing = reportRepository.findByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(
                company.getId(), year, period, reportType);
        CompanyFinancialReport report = existing.orElseGet(() -> CompanyFinancialReport.builder()
                .company(company)
                .periodYear(year)
                .periodQuarter(period)
                .reportType(reportType)
                .createdAt(OffsetDateTime.now())
                .build());
        report.setSourceUrl(SOURCE_URL);
        report.setParseStatus(ParseStatus.SUCCESS);
        report.setLastCheckedAt(OffsetDateTime.now());
        return new ReportUpsert(reportRepository.save(report), existing.isEmpty());
    }

    private boolean upsertValue(CompanyFinancialReport report, ParsedFinancialItem item) {
        Optional<CompanyFinancialValue> existing = valueRepository.findByReportIdAndItemKey(report.getId(), item.itemKey().name());
        CompanyFinancialValue value = existing.orElseGet(() -> CompanyFinancialValue.builder()
                .report(report)
                .itemKey(item.itemKey().name())
                .createdAt(OffsetDateTime.now())
                .build());
        value.setRawLabel(item.rawLabel());
        value.setValue(item.value());
        value.setCurrency(item.currency());
        value.setUnitMultiplier(item.unitMultiplier());
        value.setCurrentPeriod(true);
        valueRepository.save(value);
        return existing.isPresent();
    }

    private ReportType reportType(Integer period) {
        return switch (period != null ? period : 4) {
            case 1 -> ReportType.Q1;
            case 2 -> ReportType.Q2;
            case 3 -> ReportType.Q3;
            default -> ReportType.ANNUAL;
        };
    }

    private String periodLabel(int period) {
        return period == 4 ? "ANNUAL" : "Q" + period;
    }

    private record ReportUpsert(CompanyFinancialReport report, boolean created) {
    }
}
