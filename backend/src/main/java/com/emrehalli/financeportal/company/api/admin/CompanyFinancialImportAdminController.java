package com.emrehalli.financeportal.company.api.admin;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportResponse;
import com.emrehalli.financeportal.company.importcsv.CompanyFinancialImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/companies/financials")
public class CompanyFinancialImportAdminController {

    private final CompanyFinancialImportService importService;

    public CompanyFinancialImportAdminController(CompanyFinancialImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ManualFinancialImportResponse> importFinancials(@RequestParam("file") MultipartFile file,
                                                                       @RequestParam(defaultValue = "false") boolean dryRun,
                                                                       @RequestParam(defaultValue = "true") boolean replaceExisting,
                                                                       @RequestParam(defaultValue = "true") boolean recalculateRatios) {
        ManualFinancialImportResponse result = importService.importCsv(file, dryRun, replaceExisting, recalculateRatios);
        return ApiResponse.<ManualFinancialImportResponse>builder()
                .success(true)
                .data(result)
                .message(dryRun ? "CSV import dry-run tamamlandı." : "CSV import tamamlandı.")
                .build();
    }
}


