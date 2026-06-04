package com.emrehalli.financeportal.ai.features.news;

import java.util.List;

public record NewsImpactContext(
        Long newsId,
        String title,
        String newsSummary,
        String source,
        String relatedSymbol,
        NewsCategory detectedCategory,
        List<String> affectedSectors,
        String initialSentiment,
        String initialRiskLevel
) {}




