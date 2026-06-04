package com.emrehalli.financeportal.ai.features.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardAiInputContext(
        boolean hasPortfolio,
        BigDecimal totalValue,
        BigDecimal dailyProfitLoss,
        BigDecimal totalProfitLoss,
        int holdingCount,
        List<HoldingSnapshot> topHoldings,
        BigDecimal avgMarketChange,
        int gainerCount,
        int loserCount,
        int totalQuotes,
        List<String> recentNewsTitles
) {
    public record HoldingSnapshot(
            String symbol,
            String instrumentType,
            BigDecimal weightPercent,
            BigDecimal dailyChangePercent
    ) {}
}
