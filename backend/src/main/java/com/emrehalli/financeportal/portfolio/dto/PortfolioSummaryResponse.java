package com.emrehalli.financeportal.portfolio.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PortfolioSummaryResponse {

    private BigDecimal totalCost;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;

    // backward compatibility
    private BigDecimal totalCurrentValue;
    private BigDecimal totalProfitLoss;

    public PortfolioSummaryResponse() {
    }

    public PortfolioSummaryResponse(BigDecimal totalCost, BigDecimal currentValue, BigDecimal profitLoss, BigDecimal profitLossPercent) {
        this.totalCost = totalCost;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;

        this.totalCurrentValue = currentValue;
        this.totalProfitLoss = profitLoss;
    }
}