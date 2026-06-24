package com.emrehalli.financeportal.company.api.admin;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyRatioCalculationResponse;
import com.emrehalli.financeportal.company.service.CompanyRatioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Oran Hesaplama", description = "Şirket finansal oran hesaplama")
@RestController
@RequestMapping("/api/v1/admin/companies")
public class CompanyRatioAdminController {

    private final CompanyRatioService ratioService;

    public CompanyRatioAdminController(CompanyRatioService ratioService) {
        this.ratioService = ratioService;
    }

    @Operation(summary = "Şirket oranlarını hesapla")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Oranlar başarıyla hesaplandı"))
    @PostMapping("/{ticker}/ratios/calculate")
    public ApiResponse<CompanyRatioCalculationResponse> calculate(@PathVariable String ticker) {
        CompanyRatioCalculationResponse result = ratioService.calculateForTicker(ticker);
        return ApiResponse.<CompanyRatioCalculationResponse>builder()
                .success(true)
                .data(result)
                .message(result.isCalculated() ? "Oran hesaplandÄ±." : result.getFailedReason())
                .build();
    }

    @Operation(summary = "Tüm şirketlerin oranlarını hesapla")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tüm şirketlerin oranları başarıyla hesaplandı"))
    @PostMapping("/ratios/calculate-all")
    public ApiResponse<List<CompanyRatioCalculationResponse>> calculateAll() {
        List<CompanyRatioCalculationResponse> results = ratioService.calculateForAllActiveCompanies();
        long successCount = results.stream().filter(CompanyRatioCalculationResponse::isCalculated).count();
        return ApiResponse.<List<CompanyRatioCalculationResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d ÅŸirket iÅŸlendi. BaÅŸarÄ±lÄ±: %d", results.size(), successCount))
                .build();
    }

    @Operation(summary = "Şirket oranlarını yeniden hesapla")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Oranlar başarıyla yeniden hesaplandı"))
    @PatchMapping("/{ticker}/recalculate-ratios")
    public ApiResponse<CompanyRatioCalculationResponse> recalculate(@PathVariable String ticker) {
        CompanyRatioCalculationResponse result = ratioService.recalculateLatestForTicker(ticker);
        return ApiResponse.<CompanyRatioCalculationResponse>builder()
                .success(true)
                .data(result)
                .message(result.isCalculated() ? "Oran yeniden hesaplandÄ±." : result.getFailedReason())
                .build();
    }
}

