package com.emrehalli.financeportal.ai.dto;

import java.util.List;

public record AiFundamentalAnalysisResponse(
        String symbol,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> risks,
        String growthComment,
        FinancialHealth financialHealth,
        String disclaimer,
        AiResponseMetadata metadata
) {
    public enum FinancialHealth {
        STRONG,
        STABLE,
        WATCH,
        RISKY
    }
}
