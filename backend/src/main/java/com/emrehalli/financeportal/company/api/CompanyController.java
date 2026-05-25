package com.emrehalli.financeportal.company.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyDisclosureResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyFinancialReportResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyProfileResponse;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyQueryService companyQueryService;

    public CompanyController(CompanyQueryService companyQueryService) {
        this.companyQueryService = companyQueryService;
    }

    @GetMapping
    public ApiResponse<List<CompanyProfileResponse>> listCompanies() {
        return ApiResponse.<List<CompanyProfileResponse>>builder()
                .success(true)
                .data(companyQueryService.listActiveCompanies())
                .build();
    }

    @GetMapping("/{ticker}")
    public ApiResponse<CompanyProfileResponse> getCompany(@PathVariable String ticker) {
        return ApiResponse.<CompanyProfileResponse>builder()
                .success(true)
                .data(companyQueryService.getCompany(ticker))
                .build();
    }

    @GetMapping("/{ticker}/financials")
    public ApiResponse<List<CompanyFinancialReportResponse>> getFinancials(@PathVariable String ticker) {
        return ApiResponse.<List<CompanyFinancialReportResponse>>builder()
                .success(true)
                .data(companyQueryService.getFinancials(ticker))
                .build();
    }

    @GetMapping("/{ticker}/disclosures")
    public ApiResponse<Page<CompanyDisclosureResponse>> getDisclosures(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CompanyDisclosureResponse> result = companyQueryService.getDisclosures(
                ticker, PageRequest.of(page, size));
        return ApiResponse.<Page<CompanyDisclosureResponse>>builder()
                .success(true)
                .data(result)
                .build();
    }

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



