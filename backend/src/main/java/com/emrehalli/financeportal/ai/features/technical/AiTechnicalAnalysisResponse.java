package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

public record AiTechnicalAnalysisResponse(
        String symbol,
        String summary,
        String trendComment,
        String momentumComment,
        RiskLevel riskLevel,
        AiSignal signal,
        String disclaimer,
        AiResponseMetadata metadata,
        String keyObservation
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
