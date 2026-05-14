package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.FinancialReportSyncResponse;
import com.emrehalli.financeportal.company.service.CompanyFinancialReportSyncService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class KapFinancialAdminController {

    private final CompanyFinancialReportSyncService syncService;

    public KapFinancialAdminController(CompanyFinancialReportSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{ticker}/financial-reports/sync")
    public ApiResponse<FinancialReportSyncResponse> syncReports(@PathVariable String ticker) {
        FinancialReportSyncResponse result = syncService.syncReportsForTicker(ticker);
        return ApiResponse.<FinancialReportSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/financial-reports/sync-all")
    public ApiResponse<List<FinancialReportSyncResponse>> syncAllReports() {
        List<FinancialReportSyncResponse> results = syncService.syncReportsForAllActiveCompanies();
        int totalSaved = results.stream().mapToInt(FinancialReportSyncResponse::getSavedReports).sum();
        return ApiResponse.<List<FinancialReportSyncResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Toplam kaydedilen rapor: %d", results.size(), totalSaved))
                .build();
    }

    @PostMapping("/{ticker}/financial-reports/parse-pending")
    public ApiResponse<FinancialReportSyncResponse> parsePending(@PathVariable String ticker) {
        FinancialReportSyncResponse result = syncService.parsePendingReportsForTicker(ticker);
        return ApiResponse.<FinancialReportSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/financial-reports/parse-pending-all")
    public ApiResponse<List<FinancialReportSyncResponse>> parsePendingAll() {
        List<FinancialReportSyncResponse> results = syncService.parsePendingReportsForAllActiveCompanies();
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
