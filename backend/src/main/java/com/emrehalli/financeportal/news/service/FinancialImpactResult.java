package com.emrehalli.financeportal.news.service;

import java.util.List;

public record FinancialImpactResult(
        boolean marketRelevant,
        String confidence,
        ImpactType impactType,
        List<AffectedAssetClass> affectedAssetClasses,
        int score,
        String reason,
        List<String> matchedSignals
) {
    static FinancialImpactResult notRelevant(String reason) {
        return new FinancialImpactResult(false, "LOW", ImpactType.NOT_MARKET_RELEVANT,
                List.of(AffectedAssetClass.NONE), 0, reason, List.of());
    }

    static FinancialImpactResult ofKap() {
        return new FinancialImpactResult(true, "HIGH", ImpactType.DIRECT_COMPANY,
                List.of(AffectedAssetClass.STOCK), 90,
                "KAP resmi bildirimi â€“ her zaman piyasa etkili", List.of("KAP"));
    }
}




