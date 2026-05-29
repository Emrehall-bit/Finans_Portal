package com.emrehalli.financeportal.ai.dto;

public record AiTechnicalAnalysisResponse(
        String symbol,
        String summary,
        String trendComment,
        String momentumComment,
        RiskLevel riskLevel,
        AiSignal signal,
        String disclaimer,
        AiResponseMetadata metadata
) {
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum AiSignal {
        POSITIVE,
        NEUTRAL,
        NEGATIVE,
        RISKY
    }
}




