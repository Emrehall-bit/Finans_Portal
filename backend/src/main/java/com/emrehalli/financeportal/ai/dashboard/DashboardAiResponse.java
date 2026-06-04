package com.emrehalli.financeportal.ai.dashboard;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;

import java.util.List;

public record DashboardAiResponse(
        String marketContext,
        String newsContext,
        String portfolioImpact,
        List<String> riskSignals,
        List<String> watchPoints,
        String finalComment,
        MarketTone marketTone,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {
    public enum MarketTone {
        POSITIVE, NEUTRAL, CAUTIOUS, NEGATIVE
    }
}
