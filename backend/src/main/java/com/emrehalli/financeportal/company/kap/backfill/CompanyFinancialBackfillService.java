package com.emrehalli.financeportal.company.kap.backfill;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.kap.dto.BackfillPairResultDto;
import com.emrehalli.financeportal.company.dto.request.FinancialBackfillRequest;
import com.emrehalli.financeportal.company.kap.dto.FinancialBackfillResponse;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.kap.client.KapFinancialTableClient;
import com.emrehalli.financeportal.company.kap.client.KapFinancialTableClient.KapFinancialTableResult;
import com.emrehalli.financeportal.company.kap.client.KapFinancialTableClient.ParsedFinancialItem;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.company.support.FinancialReportUpsertSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompanyFinancialBackfillService {

    private static final Logger logger = LogManager.getLogger(CompanyFinancialBackfillService.class);
    private static final String SOURCE_URL = "https://www.kap.org.tr/tr/api/export/compareItems";
    private static final long INTER_PAIR_DELAY_MS = 1000;

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final KapFinancialTableClient financialTableClient;
    private final FinancialReportUpsertSupport upsertSupport;

    public CompanyFinancialBackfillService(CompanyProfileRepository profileRepository,
                                           CompanyFinancialReportRepository reportRepository,
                                           KapFinancialTableClient financialTableClient,
                                           FinancialReportUpsertSupport upsertSupport) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.financialTableClient = financialTableClient;
        this.upsertSupport = upsertSupport;
    }

    @Transactional
    public FinancialBackfillResponse backfill(String tickerCode, FinancialBackfillRequest request) {
        CompanyProfile company = profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));

        int endYear = request != null && request.getEndYear() != null ? request.getEndYear() : OffsetDateTime.now().getYear();
        int startYear = request != null && request.getStartYear() != null ? request.getStartYear() : endYear - 5;
        if (startYear > endYear) {
            int tmp = startYear; startYear = endYear; endYear = tmp;
        }

        List<Integer> processedYears = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            processedYears.add(y);
        }
        List<String> processedPeriods = List.of("Q1", "Q2", "Q3", "ANNUAL");

        logger.info("KAP financial backfill start. ticker={}, companyId={}, kapCompanyId={}, fromYear={}, toYear={}, processedYears={}, totalPairs={}",
                tickerCode, company.getId(), company.getKapCompanyId(),
                startYear, endYear, processedYears, processedYears.size() * 4);

        if (processedYears.isEmpty()) {
            return emptyResponse(company, processedYears, processedPeriods, "No year/period pairs processed — year range is empty.");
        }

        List<BackfillPairResultDto> pairResults = new ArrayList<>();
        int processedPairs = 0;
        int fetchedPairs = 0;
        int skippedPairs = 0;
        int failedPairs = 0;
        int xlsxFetched = 0;
        int xlsxByteSize = 0;
        int savedReports = 0;
        int savedValues = 0;
        int updatedValues = 0;
        int matchedItemCount = 0;
        List<String> sheetNames = new ArrayList<>();
        List<String> unmatchedLabelsSeen = new ArrayList<>();
        boolean interrupted = false;

        outer:
        for (int period = 1; period <= 4; period++) {
            for (int year : processedYears) {
                if (processedPairs > 0) {
                    try {
                        Thread.sleep(INTER_PAIR_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        interrupted = true;
                        logger.warn("KAP financial backfill interrupted after {} pairs. ticker={}", processedPairs, tickerCode);
                        break outer;
                    }
                }

                processedPairs++;
                String reportTypeName = periodLabel(period);
                logger.info("Backfill fetching XLSX. ticker={}, year={}, period={}, reportType={}, kapCompanyId={}",
                        tickerCode, year, period, reportTypeName, company.getKapCompanyId());

                KapFinancialTableResult result;
                try {
                    result = financialTableClient.fetchForPeriod(company, year, period);
                } catch (Exception e) {
                    String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                    logger.error("KAP financial backfill fetch FAILED. ticker={}, year={}, period={}, error={}",
                            tickerCode, year, period, errorMsg, e);
                    failedPairs++;
                    pairResults.add(BackfillPairResultDto.builder()
                            .year(year).period(period).reportType(reportTypeName)
                            .status("FAILED").errorMessage(errorMsg)
                            .xlsxByteSize(0).matchedItems(0).savedValues(0)
                            .build());
                    continue;
                }

                xlsxFetched++;
                xlsxByteSize += result.xlsxByteSize();
                if (result.sheetName() != null && !sheetNames.contains(result.sheetName())) {
                    sheetNames.add(result.sheetName());
                }
                if (result.unmatchedLabels() != null) {
                    for (String label : result.unmatchedLabels()) {
                        if (unmatchedLabelsSeen.size() < 10 && !unmatchedLabelsSeen.contains(label)) {
                            unmatchedLabelsSeen.add(label);
                        }
                    }
                }

                if (result.items().isEmpty()) {
                    String skipReason = "0 items. sheetName=" + result.sheetName()
                            + " workbookSheets=" + result.workbookSheetNames()
                            + " scannedRows=" + result.scannedRowCount()
                            + " unmatchedLabels=" + (result.unmatchedLabels() != null
                                    ? result.unmatchedLabels().stream().limit(5).toList() : List.of());
                    logger.warn("KAP financial backfill SKIPPED. ticker={}, year={}, period={}, {}",
                            tickerCode, year, period, skipReason);
                    skippedPairs++;
                    pairResults.add(BackfillPairResultDto.builder()
                            .year(year).period(period).reportType(reportTypeName)
                            .status("SKIPPED").errorMessage(skipReason)
                            .xlsxByteSize(result.xlsxByteSize())
                            .workbookSheetNames(result.workbookSheetNames())
                            .rowPreviewFirst20(result.rowPreviewFirst20())
                            .matchedLabels(result.matchedLabels())
                            .firstMatchedRow(result.firstMatchedRow())
                            .rowDumpIfEmpty(result.rowDumpIfEmpty())
                            .matchedItems(0).savedValues(0)
                            .build());
                    continue;
                }

                int pairSaved = 0;
                matchedItemCount += result.items().size();
                FinancialReportUpsertSupport.ReportUpsertResult reportUpsert = upsertReport(company, year, period);
                if (reportUpsert.created()) savedReports++;
                for (ParsedFinancialItem item : result.items().values()) {
                    boolean wasUpdate = upsertValue(reportUpsert.report(), item);
                    if (wasUpdate) {
                        updatedValues++;
                    } else {
                        savedValues++;
                    }
                    pairSaved++;
                }

                logger.info("KAP financial backfill FETCHED. ticker={}, year={}, period={}, xlsxByteSize={}, matchedItems={}, pairSaved={}",
                        tickerCode, year, period, result.xlsxByteSize(), result.items().size(), pairSaved);

                fetchedPairs++;
                pairResults.add(BackfillPairResultDto.builder()
                        .year(year).period(period).reportType(reportTypeName)
                        .status("FETCHED")
                        .xlsxByteSize(result.xlsxByteSize())
                        .workbookSheetNames(result.workbookSheetNames())
                        .matchedLabels(result.matchedLabels())
                        .firstMatchedRow(result.firstMatchedRow())
                        .matchedItems(result.items().size())
                        .savedValues(pairSaved)
                        .build());
            }
        }

        logger.info("KAP financial backfill done. ticker={}, processedPairs={}, fetchedPairs={}, skippedPairs={}, failedPairs={}, xlsxFetched={}, savedReports={}, savedValues={}",
                tickerCode, processedPairs, fetchedPairs, skippedPairs, failedPairs, xlsxFetched, savedReports, savedValues);

        String message;
        if (interrupted) {
            message = "Backfill interrupted after " + processedPairs + " pairs.";
        } else if (processedPairs == 0) {
            message = "No year/period pairs processed.";
        } else if (xlsxFetched == 0) {
            message = "Backfill completed but no XLSX fetched. Check pairResults for error details.";
        } else if (fetchedPairs == 0) {
            message = "XLSX fetched but no items matched. Check pairResults[].rowPreviewFirst20 to diagnose sheet content.";
        } else {
            message = "Finansal backfill tamamlandı.";
        }

        return FinancialBackfillResponse.builder()
                .tickerCode(company.getTickerCode())
                .processedYears(processedYears)
                .processedPeriods(processedPeriods)
                .processedPairs(processedPairs)
                .fetchedPairs(fetchedPairs)
                .skippedPairs(skippedPairs)
                .failedPairs(failedPairs)
                .parsedRows(matchedItemCount)
                .xlsxFetched(xlsxFetched)
                .xlsxByteSize(xlsxByteSize)
                .sheetNames(sheetNames.isEmpty() ? null : sheetNames)
                .matchedItemCount(matchedItemCount)
                .unmatchedLabels(unmatchedLabelsSeen.isEmpty() ? null : unmatchedLabelsSeen)
                .savedReports(savedReports)
                .savedValues(savedValues)
                .updatedValues(updatedValues)
                .pairResults(pairResults)
                .message(message)
                .build();
    }

    private FinancialBackfillResponse emptyResponse(CompanyProfile company, List<Integer> years,
                                                     List<String> periods, String message) {
        return FinancialBackfillResponse.builder()
                .tickerCode(company.getTickerCode())
                .processedYears(years).processedPeriods(periods)
                .processedPairs(0).fetchedPairs(0).skippedPairs(0).failedPairs(0)
                .parsedRows(0).xlsxFetched(0).xlsxByteSize(0)
                .matchedItemCount(0).savedReports(0).savedValues(0).updatedValues(0)
                .pairResults(List.of()).message(message)
                .build();
    }

    private FinancialReportUpsertSupport.ReportUpsertResult upsertReport(CompanyProfile company, Integer year, Integer period) {
        return upsertSupport.upsertReport(
                company,
                year,
                period,
                reportType(period),
                SOURCE_URL,
                (LocalDate) null,
                ParseStatus.SUCCESS,
                OffsetDateTime.now()
        );
    }

    private boolean upsertValue(CompanyFinancialReport report, ParsedFinancialItem item) {
        return upsertSupport.upsertValue(
                report,
                item.itemKey().name(),
                item.rawLabel(),
                item.value(),
                item.currency(),
                item.unitMultiplier(),
                true
        ).updated();
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
        return switch (period) {
            case 1 -> "Q1";
            case 2 -> "Q2";
            case 3 -> "Q3";
            default -> "ANNUAL";
        };
    }

}



