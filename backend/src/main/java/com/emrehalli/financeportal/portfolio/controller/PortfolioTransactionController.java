package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioTransactionRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioTransactionResponseDto;
import com.emrehalli.financeportal.portfolio.service.PortfolioTransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio-transactions")
public class PortfolioTransactionController {

    private final PortfolioTransactionService transactionService;

    public PortfolioTransactionController(PortfolioTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{portfolioId}")
    public ApiResponse<PortfolioTransactionResponseDto> createTransaction(@PathVariable Long portfolioId,
                                                                          @Valid @RequestBody CreatePortfolioTransactionRequest request) {
        PortfolioTransactionResponseDto dto = transactionService.createTransaction(portfolioId, request);
        return ApiResponse.<PortfolioTransactionResponseDto>builder()
                .success(true)
                .data(dto)
                .message("Transaction created successfully")
                .build();
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ApiResponse<List<PortfolioTransactionResponseDto>> getTransactions(@PathVariable Long portfolioId) {
        List<PortfolioTransactionResponseDto> data = transactionService.getTransactionsByPortfolioId(portfolioId);
        return ApiResponse.<List<PortfolioTransactionResponseDto>>builder()
                .success(true)
                .data(data)
                .message("Transactions fetched successfully")
                .build();
    }
}