package com.emrehalli.financeportal.news.service;

import java.util.List;

public record FinancialImpactResult(
        boolean marketRelevant,
        String confidence,
        ImpactType impactType,
        int score,
        String reason,
        List<String> matchedSignals
) {
    static FinancialImpactResult notRelevant(String reason) {
        return new FinancialImpactResult(false, "LOW", ImpactType.NOT_MARKET_RELEVANT, 0, reason, List.of());
    }

    static FinancialImpactResult ofKap() {
        return new FinancialImpactResult(true, "HIGH", ImpactType.DIRECT_COMPANY, 90,
                "KAP official disclosure is always market relevant", List.of("KAP"));
    }
}