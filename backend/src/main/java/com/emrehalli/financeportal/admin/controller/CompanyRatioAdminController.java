package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.CompanyRatioCalculationResponse;
import com.emrehalli.financeportal.company.service.CompanyRatioService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class CompanyRatioAdminController {

    private final CompanyRatioService ratioService;

    public CompanyRatioAdminController(CompanyRatioService ratioService) {
        this.ratioService = ratioService;
    }

    @PostMapping("/{ticker}/ratios/calculate")
    public ApiResponse<CompanyRatioCalculationResponse> calculate(@PathVariable String ticker) {
        CompanyRatioCalculationResponse result = ratioService.calculateForTicker(ticker);
        return ApiResponse.<CompanyRatioCalculationResponse>builder()
                .success(true)
                .data(result)
                .message(result.isCalculated() ? "Oran hesaplandı." : result.getFailedReason())
                .build();
    }

    @PostMapping("/ratios/calculate-all")
    public ApiResponse<List<CompanyRatioCalculationResponse>> calculateAll() {
        List<CompanyRatioCalculationResponse> results = ratioService.calculateForAllActiveCompanies();
        long successCount = results.stream().filter(CompanyRatioCalculationResponse::isCalculated).count();
        return ApiResponse.<List<CompanyRatioCalculationResponse>>builder()
                .success(true)
                .data(results)
                .message(String.format("%d şirket işlendi. Başarılı: %d", results.size(), successCount))
                .build();
    }

    @PatchMapping("/{ticker}/recalculate-ratios")
    public ApiResponse<CompanyRatioCalculationResponse> recalculate(@PathVariable String ticker) {
        CompanyRatioCalculationResponse result = ratioService.recalculateLatestForTicker(ticker);
        return ApiResponse.<CompanyRatioCalculationResponse>builder()
                .success(true)
                .data(result)
                .message(result.isCalculated() ? "Oran yeniden hesaplandı." : result.getFailedReason())
                .build();
    }
}
