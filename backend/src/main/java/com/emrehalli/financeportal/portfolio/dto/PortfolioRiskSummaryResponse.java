package com.emrehalli.financeportal.portfolio.dto;

public record PortfolioRiskSummaryResponse(
        PortfolioRiskMetricDto diversification,
        PortfolioRiskMetricDto volatility,
        PortfolioRiskMetricDto liquidity,
        String alertTitle,
        String alertMessage,
        String alertTone,
        int lookbackDays
) {
}

