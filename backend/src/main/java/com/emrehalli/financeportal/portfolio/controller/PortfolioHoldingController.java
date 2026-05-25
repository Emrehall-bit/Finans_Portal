package com.emrehalli.financeportal.portfolio.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio-holdings")
public class PortfolioHoldingController {

    private final PortfolioHoldingService holdingService;
    private final AppMessageSource appMessageSource;

    public PortfolioHoldingController(PortfolioHoldingService holdingService, AppMessageSource appMessageSource) {
        this.holdingService = holdingService;
        this.appMessageSource = appMessageSource;
    }

    @PostMapping("/{portfolioId}")
    public ApiResponse<PortfolioHoldingDto> createHolding(@PathVariable Long portfolioId,
                                                          @Valid @RequestBody CreatePortfolioHoldingRequest request) {
        PortfolioHoldingDto data = holdingService.createHolding(portfolioId, request);
        return ApiResponse.<PortfolioHoldingDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.created"))
                .build();
    }

    @PutMapping("/{portfolioId}/{holdingId}")
    public ApiResponse<PortfolioHoldingDto> updateHolding(@PathVariable Long portfolioId,
                                                          @PathVariable Long holdingId,
                                                          @Valid @RequestBody UpdatePortfolioHoldingRequest request) {
        PortfolioHoldingDto data = holdingService.updateHolding(portfolioId, holdingId, request);
        return ApiResponse.<PortfolioHoldingDto>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.updated"))
                .build();
    }

    @DeleteMapping("/{portfolioId}/{holdingId}")
    public ApiResponse<Void> deleteHolding(@PathVariable Long portfolioId,
                                           @PathVariable Long holdingId) {
        holdingService.deleteHolding(portfolioId, holdingId);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("portfolio.holding.deleted"))
                .build();
    }

    @GetMapping("/portfolio/{portfolioId}")
    public ApiResponse<List<PortfolioHoldingDto>> getHoldings(@PathVariable Long portfolioId) {
        List<PortfolioHoldingDto> data = holdingService.getHoldingsByPortfolioId(portfolioId);
        return ApiResponse.<List<PortfolioHoldingDto>>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.holding.list.fetched"))
                .build();
    }

    @GetMapping("/portfolio/{portfolioId}/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(@PathVariable Long portfolioId) {
        PortfolioSummaryResponse data = holdingService.getPortfolioSummary(portfolioId);
        return ApiResponse.<PortfolioSummaryResponse>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("portfolio.summary.fetched"))
                .build();
    }
}






