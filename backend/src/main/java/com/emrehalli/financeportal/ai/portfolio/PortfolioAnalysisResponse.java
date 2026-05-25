package com.emrehalli.financeportal.ai.portfolio;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioAnalysisResponse(
        Long portfolioId,
        String portfolioName,
        BigDecimal totalValue,
        BigDecimal totalProfitLoss,
        BigDecimal totalProfitLossPercent,
        String summary,
        String allocationComment,
        String riskComment,
        String diversificationComment,
        List<String> strongestPositions,
        List<String> weakestPositions,
        List<String> riskSignals,
        List<String> suggestions,
        String finalComment,
        DataQuality dataQuality,
        String providerUsed,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {
    public enum DataQuality {
        COMPLETE,
        PARTIAL,
        LIMITED
    }
}



