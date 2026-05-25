package com.emrehalli.financeportal.company.api.admin;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.request.FinancialBackfillRequest;
import com.emrehalli.financeportal.company.kap.backfill.CompanyDisclosureSyncService;
import com.emrehalli.financeportal.company.kap.backfill.CompanyFinancialBackfillService;
import com.emrehalli.financeportal.company.kap.backfill.CompanyFinancialReportSyncService;
import com.emrehalli.financeportal.company.kap.dto.CompanyDisclosureSyncResponse;
import com.emrehalli.financeportal.company.kap.dto.FinancialBackfillResponse;
import com.emrehalli.financeportal.company.kap.dto.FinancialReportSyncResponse;
import com.emrehalli.financeportal.company.kap.dto.KapFinancialTableDebugResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class CompanyAdminController {

    private final CompanyDisclosureSyncService disclosureSyncService;
    private final CompanyFinancialReportSyncService financialReportSyncService;
    private final CompanyFinancialBackfillService financialBackfillService;

    public CompanyAdminController(CompanyDisclosureSyncService disclosureSyncService,
                                  CompanyFinancialReportSyncService financialReportSyncService,
                                  CompanyFinancialBackfillService financialBackfillService) {
        this.disclosureSyncService = disclosureSyncService;
        this.financialReportSyncService = financialReportSyncService;
        this.financialBackfillService = financialBackfillService;
    }

    @PostMapping("/{ticker}/disclosures/sync")
    public ApiResponse<CompanyDisclosureSyncResponse> syncDisclosures(@PathVariable String ticker) {
        CompanyDisclosureSyncResponse result = disclosureSyncService.syncDisclosures(ticker);
        return ApiResponse.<CompanyDisclosureSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/{ticker}/disclosures/backfill")
    public ApiResponse<CompanyDisclosureSyncResponse> backfillDisclosures(@PathVariable String ticker,
                                                                          @RequestParam(defaultValue = "365") int days) {
        CompanyDisclosureSyncResponse result = disclosureSyncService.backfillDisclosures(ticker, days);
        return ApiResponse.<CompanyDisclosureSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/disclosures/sync-all")
    public ApiResponse<List<CompanyDisclosureSyncResponse>> syncAllDisclosures() {
        List<CompanyDisclosureSyncResponse> results = disclosureSyncService.syncAllDisclosures();
        int totalSaved = results.stream().mapToInt(CompanyDisclosureSyncResponse::getSavedCount).sum();
        int totalFailed = results.stream().mapToInt(CompanyDisclosureSyncResponse::getFailedCount).sum();
        return ApiResponse.<List<CompanyDisclosureSyncResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Toplam kaydedilen: %d, hatalı: %d",
                        results.size(), totalSaved, totalFailed))
                .build();
    }

    @PostMapping("/{ticker}/financial-reports/sync")
    public ApiResponse<FinancialReportSyncResponse> syncReports(@PathVariable String ticker) {
        FinancialReportSyncResponse result = financialReportSyncService.syncReportsForTicker(ticker);
        return ApiResponse.<FinancialReportSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/{ticker}/financials/backfill")
    public ApiResponse<FinancialBackfillResponse> backfillFinancials(@PathVariable String ticker,
                                                                     @RequestBody(required = false) FinancialBackfillRequest request) {
        FinancialBackfillResponse result = financialBackfillService.backfill(ticker, request);
        return ApiResponse.<FinancialBackfillResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/financial-reports/sync-all")
    public ApiResponse<List<FinancialReportSyncResponse>> syncAllReports() {
        List<FinancialReportSyncResponse> results = financialReportSyncService.syncReportsForAllActiveCompanies();
        int totalSaved = results.stream().mapToInt(FinancialReportSyncResponse::getSavedReports).sum();
        return ApiResponse.<List<FinancialReportSyncResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Toplam kaydedilen rapor: %d", results.size(), totalSaved))
                .build();
    }

    @PostMapping("/{ticker}/financial-reports/parse-pending")
    public ApiResponse<FinancialReportSyncResponse> parsePending(@PathVariable String ticker,
                                                                 @RequestParam(defaultValue = "false") boolean includeFailed,
                                                                 @RequestParam(defaultValue = "false") boolean forceReparse) {
        FinancialReportSyncResponse result = financialReportSyncService.parsePendingReportsForTicker(ticker, includeFailed, forceReparse);
        return ApiResponse.<FinancialReportSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/{ticker}/financial-table/debug-fetch")
    public ApiResponse<KapFinancialTableDebugResponse> debugFetchFinancialTable(@PathVariable String ticker,
                                                                                @RequestParam String year,
                                                                                @RequestParam String period) {
        KapFinancialTableDebugResponse result = financialReportSyncService.debugFetchFinancialTable(ticker, year, period);
        return ApiResponse.<KapFinancialTableDebugResponse>builder()
                .success(result.isSuccess())
                .data(result)
                .message(result.getMessage() != null
                        ? result.getMessage()
                        : result.isSuccess() ? "KAP compareItems debug fetch başarılı." : "KAP compareItems debug fetch başarısız.")
                .build();
    }

    @PostMapping("/financial-reports/parse-pending-all")
    public ApiResponse<List<FinancialReportSyncResponse>> parsePendingAll() {
        List<FinancialReportSyncResponse> results = financialReportSyncService.parsePendingReportsForAllActiveCompanies();
        int totalSuccess = results.stream().mapToInt(FinancialReportSyncResponse::getSuccessCount).sum();
        int totalFailed = results.stream().mapToInt(FinancialReportSyncResponse::getFailedCount).sum();
        return ApiResponse.<List<FinancialReportSyncResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Başarılı: %d, hatalı: %d",
                        results.size(), totalSuccess, totalFailed))
                .build();
    }
}


