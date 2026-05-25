package com.emrehalli.financeportal.company.kap.backfill;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.kap.dto.FinancialReportSyncResponse;
import com.emrehalli.financeportal.company.kap.dto.KapFinancialTableDebugResponse;
import com.emrehalli.financeportal.company.kap.dto.ParseFailureDto;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.kap.parser.FinancialItemParser;
import com.emrehalli.financeportal.company.kap.parser.ParseAttemptResult;
import com.emrehalli.financeportal.company.kap.dto.SkippedReportReasonDto;
import com.emrehalli.financeportal.company.kap.client.KapFinancialTableClient;
import com.emrehalli.financeportal.company.kap.client.KapFinancialReportProvider;
import com.emrehalli.financeportal.company.kap.client.KapFinancialReportProviderResult;
import com.emrehalli.financeportal.company.kap.dto.KapFinancialReportDto;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompanyFinancialReportSyncService {

    private static final Logger logger = LogManager.getLogger(CompanyFinancialReportSyncService.class);
    private static final long INTER_COMPANY_DELAY_MS = 500;

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final KapFinancialReportProvider reportProvider;
    private final FinancialItemParser itemParser;
    private final KapFinancialTableClient financialTableClient;

    public CompanyFinancialReportSyncService(CompanyProfileRepository profileRepository,
                                             CompanyFinancialReportRepository reportRepository,
                                             KapFinancialReportProvider reportProvider,
                                             FinancialItemParser itemParser,
                                             KapFinancialTableClient financialTableClient) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.reportProvider = reportProvider;
        this.itemParser = itemParser;
        this.financialTableClient = financialTableClient;
    }

    // -------------------------------------------------------------------------
    // Sync: discover report periods from FINANCIAL disclosures → save PENDING
    // -------------------------------------------------------------------------

    @Transactional
    public FinancialReportSyncResponse syncReportsForTicker(String tickerCode) {
        CompanyProfile company = requireCompany(tickerCode);
        KapFinancialReportProviderResult providerResult = reportProvider.findCandidateReports(company);
        List<KapFinancialReportDto> candidates = providerResult.reports();
        List<SkippedReportReasonDto> skippedReasons = new ArrayList<>(providerResult.skippedReasons());

        int savedReports = 0;
        int duplicateSkipped = 0;
        List<String> createdPeriods = new ArrayList<>();

        for (KapFinancialReportDto dto : candidates) {
            try {
                boolean exists = reportRepository.existsByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(
                        company.getId(), dto.getPeriodYear(), dto.getPeriodQuarter(), dto.getReportType());
                if (exists) {
                    duplicateSkipped++;
                    continue;
                }
                reportRepository.save(CompanyFinancialReport.builder()
                        .company(company)
                        .periodYear(dto.getPeriodYear())
                        .periodQuarter(dto.getPeriodQuarter())
                        .reportType(dto.getReportType())
                        .sourceUrl(dto.getSourceUrl())
                        .publishedAt(dto.getPublishedAt())
                        .parseStatus(ParseStatus.PENDING)
                        .createdAt(OffsetDateTime.now())
                        .build());
                savedReports++;
                createdPeriods.add(dto.getPeriodYear() + "/Q" + dto.getPeriodQuarter());
            } catch (Exception e) {
                logger.warn("Report save failed. ticker={}, year={}, q={}", tickerCode,
                        dto.getPeriodYear(), dto.getPeriodQuarter(), e);
            }
        }

        int totalDiscovered = candidates.size() + providerResult.skippedReasons().size();
        logger.info("Report sync done. ticker={}, foundFinancialDisclosureCount={}, saved={}, dupes={}, skipped={}, createdPeriods={}",
                tickerCode, totalDiscovered, savedReports, duplicateSkipped, skippedReasons.size(), createdPeriods);

        return FinancialReportSyncResponse.builder()
                .tickerCode(tickerCode)
                .discoveredReports(totalDiscovered)
                .savedReports(savedReports)
                .duplicateSkipped(duplicateSkipped)
                .skippedReasons(skippedReasons.isEmpty() ? null : skippedReasons)
                .message("Rapor sync tamamlandı.")
                .build();
    }

    public List<FinancialReportSyncResponse> syncReportsForAllActiveCompanies() {
        List<CompanyProfile> companies = profileRepository.findByActiveTrue();
        List<FinancialReportSyncResponse> results = new ArrayList<>();

        for (int i = 0; i < companies.size(); i++) {
            String ticker = companies.get(i).getTickerCode();
            try {
                results.add(syncReportsForTicker(ticker));
            } catch (Exception e) {
                logger.error("Report sync failed. ticker={}", ticker, e);
                results.add(failedSyncResponse(ticker, e));
            }
            sleepBetweenCompanies(i, companies.size(), ticker);
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Parse: run FinancialItemParser on PENDING reports
    // -------------------------------------------------------------------------

    public FinancialReportSyncResponse parsePendingReportsForTicker(String tickerCode) {
        return parsePendingReportsForTicker(tickerCode, false, false);
    }

    public FinancialReportSyncResponse parsePendingReportsForTicker(String tickerCode, boolean includeFailed) {
        return parsePendingReportsForTicker(tickerCode, includeFailed, false);
    }

    public FinancialReportSyncResponse parsePendingReportsForTicker(String tickerCode, boolean includeFailed, boolean forceReparse) {
        requireCompany(tickerCode);
        List<ParseStatus> statuses = forceReparse
                ? List.of(ParseStatus.PENDING, ParseStatus.FAILED, ParseStatus.PARTIAL, ParseStatus.SUCCESS)
                : includeFailed
                ? List.of(ParseStatus.PENDING, ParseStatus.FAILED)
                : List.of(ParseStatus.PENDING);
        List<CompanyFinancialReport> reports = reportRepository.findEligibleReports(tickerCode, statuses);

        int successCount = 0;
        int partialCount = 0;
        int failedCount = 0;
        int matchedItemCount = 0;
        int savedValueCount = 0;
        int updatedValueCount = 0;
        List<ParseFailureDto> parseFailures = new ArrayList<>();

        for (CompanyFinancialReport report : reports) {
            try {
                ParseAttemptResult result = (includeFailed || forceReparse)
                        ? itemParser.parseReport(report.getId(), true)
                        : itemParser.parsePendingReport(report.getId());
                if (result.status() == ParseStatus.SUCCESS) successCount++;
                else if (result.status() == ParseStatus.PARTIAL) partialCount++;
                else failedCount++;
                matchedItemCount += result.matchedItemCount();
                savedValueCount += result.savedValueCount();
                updatedValueCount += result.updatedValueCount();
                if (result.failure() != null) {
                    parseFailures.add(result.failure());
                }
            } catch (Exception e) {
                logger.error("Parse failed. ticker={}, reportId={}", tickerCode, report.getId(), e);
                failedCount++;
                parseFailures.add(ParseFailureDto.builder()
                        .reportId(report.getId())
                        .sourceUrl(report.getSourceUrl())
                        .periodYear(report.getPeriodYear())
                        .periodQuarter(report.getPeriodQuarter())
                        .reason("Unknown parse error")
                        .build());
            }
        }

        logger.info("Parse done. ticker={}, includeFailed={}, forceReparse={}, parsed={}, reparsed={}, success={}, partial={}, failed={}, updatedValues={}",
                tickerCode, includeFailed, forceReparse, reports.size(), forceReparse ? reports.size() : 0,
                successCount, partialCount, failedCount, updatedValueCount);

        return FinancialReportSyncResponse.builder()
                .tickerCode(tickerCode)
                .parsedReports(reports.size())
                .successCount(successCount)
                .partialCount(partialCount)
                .failedCount(failedCount)
                .matchedItemCount(matchedItemCount)
                .savedValueCount(savedValueCount)
                .reparsedCount(forceReparse ? reports.size() : 0)
                .updatedValueCount(updatedValueCount)
                .parseFailures(parseFailures.isEmpty() ? null : parseFailures)
                .message("Parse tamamlandı.")
                .build();
    }

    public List<FinancialReportSyncResponse> parsePendingReportsForAllActiveCompanies() {
        List<CompanyProfile> companies = profileRepository.findByActiveTrue();
        List<FinancialReportSyncResponse> results = new ArrayList<>();

        for (int i = 0; i < companies.size(); i++) {
            String ticker = companies.get(i).getTickerCode();
            try {
                results.add(parsePendingReportsForTicker(ticker));
            } catch (Exception e) {
                logger.error("Parse-all failed. ticker={}", ticker, e);
                results.add(failedParseResponse(ticker, e));
            }
            sleepBetweenCompanies(i, companies.size(), ticker);
        }
        return results;
    }

    public KapFinancialTableDebugResponse debugFetchFinancialTable(String tickerCode, String year, String period) {
        CompanyProfile company = requireCompany(tickerCode);
        KapFinancialTableDebugResponse result = financialTableClient.debugFetch(company, year, period);
        if (result.isSuccess() && !reportExists(company, year, period)) {
            return KapFinancialTableDebugResponse.builder()
                    .success(result.isSuccess())
                    .httpStatus(result.getHttpStatus())
                    .contentType(result.getContentType())
                    .xlsxByteSize(result.getXlsxByteSize())
                    .payload(result.getPayload())
                    .errorBody(result.getErrorBody())
                    .message("Bu dönem için company_financial_reports kaydı bulunmuyor. Önce Finansal Raporları Senkronize Et çalıştırın.")
                    .build();
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CompanyProfile requireCompany(String tickerCode) {
        return profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));
    }

    private boolean reportExists(CompanyProfile company, String year, String period) {
        try {
            return reportRepository.existsByCompanyIdAndPeriodYearAndPeriodQuarter(
                    company.getId(),
                    Integer.parseInt(year),
                    Integer.parseInt(period));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void sleepBetweenCompanies(int index, int total, String ticker) {
        if (index < total - 1) {
            try {
                Thread.sleep(INTER_COMPANY_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("All-companies operation interrupted after ticker={}", ticker);
            }
        }
    }

    private FinancialReportSyncResponse failedSyncResponse(String ticker, Exception e) {
        return FinancialReportSyncResponse.builder()
                .tickerCode(ticker)
                .message("Hata: " + e.getMessage())
                .build();
    }

    private FinancialReportSyncResponse failedParseResponse(String ticker, Exception e) {
        return FinancialReportSyncResponse.builder()
                .tickerCode(ticker)
                .failedCount(1)
                .message("Hata: " + e.getMessage())
                .build();
    }
}



