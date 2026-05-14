package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.FinancialReportSyncResponse;
import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.parser.FinancialItemParser;
import com.emrehalli.financeportal.company.provider.kap.KapFinancialReportProvider;
import com.emrehalli.financeportal.company.provider.kap.dto.KapFinancialReportDto;
import com.emrehalli.financeportal.company.repository.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.repository.CompanyProfileRepository;
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

    public CompanyFinancialReportSyncService(CompanyProfileRepository profileRepository,
                                             CompanyFinancialReportRepository reportRepository,
                                             KapFinancialReportProvider reportProvider,
                                             FinancialItemParser itemParser) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.reportProvider = reportProvider;
        this.itemParser = itemParser;
    }

    // -------------------------------------------------------------------------
    // Sync: discover report periods from FINANCIAL disclosures → save PENDING
    // -------------------------------------------------------------------------

    @Transactional
    public FinancialReportSyncResponse syncReportsForTicker(String tickerCode) {
        CompanyProfile company = requireCompany(tickerCode);
        List<KapFinancialReportDto> candidates = reportProvider.findCandidateReports(company);

        int savedReports = 0;
        int duplicateSkipped = 0;

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
            } catch (Exception e) {
                logger.warn("Report save failed. ticker={}, year={}, q={}", tickerCode,
                        dto.getPeriodYear(), dto.getPeriodQuarter(), e);
            }
        }

        logger.info("Report sync done. ticker={}, discovered={}, saved={}, dupes={}",
                tickerCode, candidates.size(), savedReports, duplicateSkipped);

        return FinancialReportSyncResponse.builder()
                .tickerCode(tickerCode)
                .discoveredReports(candidates.size())
                .savedReports(savedReports)
                .duplicateSkipped(duplicateSkipped)
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
        requireCompany(tickerCode);
        List<CompanyFinancialReport> pending = reportRepository
                .findByCompanyTickerCodeIgnoreCaseAndParseStatus(tickerCode, ParseStatus.PENDING);

        int successCount = 0;
        int partialCount = 0;
        int failedCount = 0;

        for (CompanyFinancialReport report : pending) {
            try {
                ParseStatus result = itemParser.parsePendingReport(report.getId());
                if (result == ParseStatus.SUCCESS) successCount++;
                else if (result == ParseStatus.PARTIAL) partialCount++;
                else failedCount++;
            } catch (Exception e) {
                logger.error("Parse failed. ticker={}, reportId={}", tickerCode, report.getId(), e);
                failedCount++;
            }
        }

        logger.info("Parse done. ticker={}, parsed={}, success={}, partial={}, failed={}",
                tickerCode, pending.size(), successCount, partialCount, failedCount);

        return FinancialReportSyncResponse.builder()
                .tickerCode(tickerCode)
                .parsedReports(pending.size())
                .successCount(successCount)
                .partialCount(partialCount)
                .failedCount(failedCount)
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CompanyProfile requireCompany(String tickerCode) {
        return profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));
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
