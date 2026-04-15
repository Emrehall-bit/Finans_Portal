package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.service.PortfolioTransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio-holdings")
public class PortfolioHoldingController {

    private final PortfolioTransactionService transactionService;

    public PortfolioHoldingController(PortfolioTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ApiResponse<List<PortfolioHoldingDto>> getHoldings(@PathVariable Long portfolioId) {
        List<PortfolioHoldingDto> data = transactionService.getHoldingsByPortfolioId(portfolioId);
        return ApiResponse.<List<PortfolioHoldingDto>>builder()
                .success(true)
                .data(data)
                .message("Holdings fetched successfully")
                .build();
    }

    @GetMapping("/portfolio/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse data = transactionService.getPortfolioSummary(portfolioId);
        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(data)
                .message("Portfolio summary fetched successfully")
                .build();
    }
}