package com.emrehalli.financeportal.company.api.admin;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.response.BasicProfileSeedResponse;
import com.emrehalli.financeportal.company.dto.response.MockRatioSeedResponse;
import com.emrehalli.financeportal.company.dto.response.ShareCountImportResponse;
import com.emrehalli.financeportal.company.service.CompanyMockRatioSeedService;
import com.emrehalli.financeportal.company.service.CompanyProfileAdminService;
import com.emrehalli.financeportal.company.service.CompanyShareCountImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Şirket Yönetimi", description = "Şirket veri yönetimi")
@RestController
@RequestMapping("/api/v1/admin/companies")
public class CompanyAdminController {

    private final CompanyProfileAdminService companyProfileAdminService;
    private final CompanyShareCountImportService companyShareCountImportService;
    private final CompanyMockRatioSeedService companyMockRatioSeedService;

    @Value("${company.features.mock-ratio-seed-enabled:false}")
    private boolean mockRatioSeedEnabled;

    public CompanyAdminController(CompanyProfileAdminService companyProfileAdminService,
                                  CompanyShareCountImportService companyShareCountImportService,
                                  CompanyMockRatioSeedService companyMockRatioSeedService) {
        this.companyProfileAdminService = companyProfileAdminService;
        this.companyShareCountImportService = companyShareCountImportService;
        this.companyMockRatioSeedService = companyMockRatioSeedService;
    }

    @Operation(summary = "Temel şirket profillerini oluştur")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Şirket profilleri başarıyla oluşturuldu"))
    @PostMapping("/seed-basic-profiles")
    public ApiResponse<BasicProfileSeedResponse> seedBasicProfiles() {
        BasicProfileSeedResponse result = companyProfileAdminService.seedBasicProfiles();
        return ApiResponse.<BasicProfileSeedResponse>builder()
                .success(true)
                .data(result)
                .message(String.format("Temel company profile seed tamamlandi. Created: %d, skippedExisting: %d, errors: %d",
                        result.getCreated(), result.getSkippedExisting(), result.getErrors() != null ? result.getErrors().size() : 0))
                .build();
    }

    @Operation(summary = "Test amaçlı sahte oran verisi oluştur")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sahte oran verileri başarıyla oluşturuldu"))
    @PostMapping("/seed-mock-ratios")
    public ApiResponse<MockRatioSeedResponse> seedMockRatios() {
        if (!mockRatioSeedEnabled) {
            throw new BadRequestException("Mock ratio seed bu ortamda devre disi.");
        }
        MockRatioSeedResponse result = companyMockRatioSeedService.seedMockRatios();
        return ApiResponse.<MockRatioSeedResponse>builder()
                .success(true)
                .data(result)
                .message(String.format("Mock ratio seed tamamlandi. Profiles: %d, Created: %d, Skipped: %d, Errors: %d",
                        result.getAutoCreatedProfiles(), result.getCreatedMockRatios(),
                        result.getSkippedExistingRatios(), result.getErrors() != null ? result.getErrors().size() : 0))
                .build();
    }

    @Operation(summary = "CSV'den hisse adedi içe aktar")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hisse adetleri başarıyla içe aktarıldı"))
    @PostMapping("/import-share-counts")
    public ApiResponse<ShareCountImportResponse> importShareCounts(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                                   @RequestParam(defaultValue = "false") boolean overwrite) {
        ShareCountImportResponse result = companyShareCountImportService.importShareCounts(file, overwrite);
        return ApiResponse.<ShareCountImportResponse>builder()
                .success(true)
                .data(result)
                .message(String.format("Share count import tamamlandi. Updated: %d, skipped: %d, notFound: %d, invalid: %d",
                        result.getUpdatedShareCounts() != null ? result.getUpdatedShareCounts().size() : 0,
                        result.getSkippedExisting() != null ? result.getSkippedExisting().size() : 0,
                        result.getNotFoundTickers() != null ? result.getNotFoundTickers().size() : 0,
                        result.getInvalidRows() != null ? result.getInvalidRows().size() : 0))
                .build();
    }
}
