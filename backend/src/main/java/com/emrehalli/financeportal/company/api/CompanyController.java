package com.emrehalli.financeportal.company.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyFinancialReportResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyProfileResponse;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Şirket Bilgileri", description = "BIST şirket profilleri, finansal raporlar ve temel veriler")
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyQueryService companyQueryService;

    public CompanyController(CompanyQueryService companyQueryService) {
        this.companyQueryService = companyQueryService;
    }

    @Operation(summary = "Şirketleri listele")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Şirketler başarıyla listelendi"))
    @GetMapping
    public ApiResponse<List<CompanyProfileResponse>> listCompanies() {
        return ApiResponse.<List<CompanyProfileResponse>>builder()
                .success(true)
                .data(companyQueryService.listActiveCompanies())
                .build();
    }

    @Operation(summary = "Şirket profilini getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Şirket profili başarıyla getirildi"))
    @GetMapping("/{ticker}")
    public ApiResponse<CompanyProfileResponse> getCompany(@PathVariable String ticker) {
        return ApiResponse.<CompanyProfileResponse>builder()
                .success(true)
                .data(companyQueryService.getCompany(ticker))
                .build();
    }

    @Operation(summary = "Şirket finansal raporlarını getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Finansal raporlar başarıyla getirildi"))
    @GetMapping("/{ticker}/financials")
    public ApiResponse<List<CompanyFinancialReportResponse>> getFinancials(@PathVariable String ticker) {
        return ApiResponse.<List<CompanyFinancialReportResponse>>builder()
                .success(true)
                .data(companyQueryService.getFinancials(ticker))
                .build();
    }

    @Operation(summary = "Şirket temel verilerini getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Temel veriler başarıyla getirildi"))
    @GetMapping("/{ticker}/fundamentals")
    public ApiResponse<CompanyFundamentalsResponse> getFundamentals(@PathVariable String ticker) {
        CompanyFundamentalsResponse data = companyQueryService.getFundamentals(ticker);
        return ApiResponse.<CompanyFundamentalsResponse>builder()
                .success(true)
                .data(data)
                .message(data.getMessage())
                .build();
    }
}

