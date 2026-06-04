package com.emrehalli.financeportal.ai.features.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PortfolioAnalysisContext(
        Long portfolioId,
        String portfolioName,
        BigDecimal totalValue,
        BigDecimal totalProfitLoss,
        BigDecimal totalProfitLossPercent,
        int holdingCount,
        int valuedHoldingCount,
        List<PositionSnapshot> positions,
        Map<String, BigDecimal> instrumentWeights,
        Map<String, BigDecimal> typeWeights,
        List<String> riskSignals,
        List<String> suggestions,
        PortfolioAnalysisResponse.DataQuality dataQuality
) {
    public record PositionSnapshot(
            String symbol,
            String instrumentType,
            BigDecimal quantity,
            BigDecimal averageBuyPrice,
            BigDecimal currentPrice,
            BigDecimal currentValue,
            BigDecimal profitLoss,
            BigDecimal profitLossPercent,
            BigDecimal weightPercent,
            boolean valuationAvailable
    ) {
    }
}




