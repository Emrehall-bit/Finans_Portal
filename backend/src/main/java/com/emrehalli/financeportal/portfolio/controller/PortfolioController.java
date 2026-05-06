package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioDetailResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioResponseDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import com.emrehalli.financeportal.portfolio.service.PortfolioValuationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingService portfolioHoldingService;
    private final AppMessageSource appMessageSource;

    public PortfolioController(PortfolioService portfolioService,
                               PortfolioHoldingService portfolioHoldingService,
                               AppMessageSource appMessageSource) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingService = portfolioHoldingService;
        this.appMessageSource = appMessageSource;
    }

    @PostMapping("/{userId}")
    public ApiResponse<PortfolioResponseDto> createPortfolio(@PathVariable Long userId,
                                                             @Valid @RequestBody CreatePortfolioRequest request) {

        PortfolioResponseDto portfolio = portfolioService.createPortfolio(userId, request);

        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message(appMessageSource.get("portfolio.created"))
                .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<PortfolioResponseDto>> getUserPortfolios(@PathVariable Long userId) {
        List<PortfolioResponseDto> portfolios = portfolioService.getPortfoliosByUserId(userId);

        return ApiResponse.<List<PortfolioResponseDto>>builder()
                .success(true)
                .data(portfolios)
                .message(appMessageSource.get("portfolio.list.fetched"))
                .build();
    }

    @PutMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponseDto> updatePortfolio(@PathVariable Long portfolioId,
                                                             @Valid @RequestBody UpdatePortfolioRequest request) {
        PortfolioResponseDto portfolio = portfolioService.updatePortfolio(portfolioId, request);
        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message(appMessageSource.get("portfolio.updated"))
                .build();
    }

    @DeleteMapping("/{portfolioId}")
    public ApiResponse<Void> deletePortfolio(@PathVariable Long portfolioId) {
        portfolioService.deletePortfolio(portfolioId);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("portfolio.deleted"))
                .build();
    }

    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponseDto> getPortfolioById(@PathVariable Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioResponseDto data = PortfolioResponseDto.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getPortfolioName())
                .visibilityStatus(portfolio.getVisibilityStatus())
                .createdAt(portfolio.getCreatedAt())
                .userId(portfolio.getUser() != null ? portfolio.getUser().getId() : null)
                .build();
        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.fetched"))
                .build();
    }

    @GetMapping("/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse summary = portfolioHoldingService.getPortfolioSummary(portfolioId);

        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(summary)
                .message(appMessageSource.get("portfolio.summary.fetched"))
                .build();
    }

    @GetMapping("/{portfolioId}/details")
    public ApiResponse<PortfolioDetailResponse> getPortfolioDetails(@PathVariable Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioValuationResult valuation = portfolioHoldingService.getPortfolioValuation(portfolioId);

        PortfolioDetailResponse response = PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getPortfolioName())
                .visibilityStatus(portfolio.getVisibilityStatus())
                .createdAt(portfolio.getCreatedAt())
                .summary(valuation.summary())
                .holdings(valuation.holdings())
                .build();

        return ApiResponse.<PortfolioDetailResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("portfolio.details.fetched"))
                .build();
    }
}



