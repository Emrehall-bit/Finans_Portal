package com.emrehalli.financeportal.company.api.admin;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportResponse;
import com.emrehalli.financeportal.company.importcsv.CompanyFinancialImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin - Finansal İçe Aktarma", description = "Şirket finansal veri içe aktarma")
@RestController
@RequestMapping("/api/v1/admin/companies/financials")
public class CompanyFinancialImportAdminController {

    private final CompanyFinancialImportService importService;

    public CompanyFinancialImportAdminController(CompanyFinancialImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Finansal verileri içe aktar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Finansal veriler başarıyla içe aktarıldı"))
    @PostMapping("/import")
    public ApiResponse<ManualFinancialImportResponse> importFinancials(@RequestParam("file") MultipartFile file,
                                                                       @RequestParam(defaultValue = "false") boolean dryRun,
                                                                       @RequestParam(defaultValue = "true") boolean replaceExisting,
                                                                       @RequestParam(defaultValue = "true") boolean recalculateRatios,
                                                                       @RequestParam(defaultValue = "false") boolean overwriteShareCount) {
        ManualFinancialImportResponse result = importService.importFile(file, dryRun, replaceExisting, recalculateRatios, overwriteShareCount);
        return ApiResponse.<ManualFinancialImportResponse>builder()
                .success(true)
                .data(result)
                .message(dryRun ? "Finansal import dry-run tamamlandi." : "Finansal import tamamlandi.")
                .build();
    }
}

