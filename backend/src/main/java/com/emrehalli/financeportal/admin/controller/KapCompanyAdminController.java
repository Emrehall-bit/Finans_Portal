package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.CompanyDisclosureSyncResponse;
import com.emrehalli.financeportal.company.service.CompanyDisclosureSyncService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class KapCompanyAdminController {

    private final CompanyDisclosureSyncService syncService;

    public KapCompanyAdminController(CompanyDisclosureSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{ticker}/disclosures/sync")
    public ApiResponse<CompanyDisclosureSyncResponse> syncDisclosures(@PathVariable String ticker) {
        CompanyDisclosureSyncResponse result = syncService.syncDisclosures(ticker);
        return ApiResponse.<CompanyDisclosureSyncResponse>builder()
                .success(true)
                .data(result)
                .message(result.getMessage())
                .build();
    }

    @PostMapping("/disclosures/sync-all")
    public ApiResponse<List<CompanyDisclosureSyncResponse>> syncAllDisclosures() {
        List<CompanyDisclosureSyncResponse> results = syncService.syncAllDisclosures();
        int totalSaved = results.stream().mapToInt(CompanyDisclosureSyncResponse::getSavedCount).sum();
        int totalFailed = results.stream().mapToInt(CompanyDisclosureSyncResponse::getFailedCount).sum();
        return ApiResponse.<List<CompanyDisclosureSyncResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Toplam kaydedilen: %d, hatalı: %d",
                        results.size(), totalSaved, totalFailed))
                .build();
    }
}
