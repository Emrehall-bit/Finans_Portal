package com.emrehalli.financeportal.ai.comparison;

import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonAnalysisContext(
        InstrumentSnapshot left,
        InstrumentSnapshot right,
        DataQuality dataQuality
) {
    public record InstrumentSnapshot(
            String symbol,
            String displayName,
            boolean technicalAvailable,
            BigDecimal latestPrice,
            BigDecimal rsi,
            String trendLabel,
            AiSignal technicalSignal,
            RiskLevel technicalRisk,
            List<String> technicalStrengths,
            List<String> technicalWeaknesses,
            boolean fundamentalsAvailable,
            FinancialHealth financialHealth,
            List<String> fundamentalStrengths,
            List<String> fundamentalWeaknesses,
            List<String> fundamentalRisks,
            int overallRiskScore
    ) {}
}



