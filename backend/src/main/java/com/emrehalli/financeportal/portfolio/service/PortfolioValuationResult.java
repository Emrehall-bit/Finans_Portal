package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;

import java.util.List;

public record PortfolioValuationResult(
        List<PortfolioHoldingDto> holdings,
        PortfolioSummaryResponse summary
) {
    public PortfolioValuationResult {
        holdings = holdings == null ? List.of() : List.copyOf(holdings);
    }
}



