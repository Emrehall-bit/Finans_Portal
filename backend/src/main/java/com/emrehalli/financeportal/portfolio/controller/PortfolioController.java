package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioDetailResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioResponseDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingService portfolioHoldingService;

    public PortfolioController(PortfolioService portfolioService,
                               PortfolioHoldingService portfolioHoldingService) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingService = portfolioHoldingService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<PortfolioResponseDto> createPortfolio(@PathVariable Long userId,
                                                             @Valid @RequestBody CreatePortfolioRequest request) {

        PortfolioResponseDto portfolio = portfolioService.createPortfolio(userId, request);

        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message("Portfolio created successfully")
                .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<PortfolioResponseDto>> getUserPortfolios(@PathVariable Long userId) {
        List<PortfolioResponseDto> portfolios = portfolioService.getPortfoliosByUserId(userId);

        return ApiResponse.<List<PortfolioResponseDto>>builder()
                .success(true)
                .data(portfolios)
                .message("User portfolios fetched successfully")
                .build();
    }

    @PutMapping("/{portfolioId}")
    public ApiResponse<PortfolioResponseDto> updatePortfolio(@PathVariable Long portfolioId,
                                                             @Valid @RequestBody UpdatePortfolioRequest request) {
        PortfolioResponseDto portfolio = portfolioService.updatePortfolio(portfolioId, request);
        return ApiResponse.<PortfolioResponseDto>builder()
                .success(true)
                .data(portfolio)
                .message("Portfolio updated successfully")
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
                .message("Portfolio fetched successfully")
                .build();
    }

    @GetMapping("/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse summary = portfolioHoldingService.getPortfolioSummary(portfolioId);

        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(summary)
                .message("Portfolio summary fetched successfully")
                .build();
    }

    @GetMapping("/{portfolioId}/details")
    public ApiResponse<PortfolioDetailResponse> getPortfolioDetails(@PathVariable Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioSummaryResponse summary = portfolioHoldingService.getPortfolioSummary(portfolioId);
        List<PortfolioHoldingDto> holdings = portfolioHoldingService.getHoldingsByPortfolioId(portfolioId);

        PortfolioDetailResponse response = PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getPortfolioName())
                .visibilityStatus(portfolio.getVisibilityStatus())
                .createdAt(portfolio.getCreatedAt())
                .summary(summary)
                .holdings(holdings)
                .build();

        return ApiResponse.<PortfolioDetailResponse>builder()
                .success(true)
                .data(response)
                .message("Portfolio details fetched successfully")
                .build();
    }
}



