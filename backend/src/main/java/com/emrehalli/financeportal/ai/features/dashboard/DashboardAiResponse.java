package com.emrehalli.financeportal.ai.features.dashboard;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

import java.util.List;

public record DashboardAiResponse(
        String marketContext,
        String newsContext,
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
