package com.emrehalli.financeportal.ai.features.news;

import java.util.List;

public record NewsImpactContext(
        Long newsId,
        String title,
        String newsSummary,
        String source,
        String relatedSymbol,
        NewsCategory detectedCategory,
        boolean titleMatched,
        boolean summaryMatched,
        boolean categoryOnly,
        List<String> affectedSectors
) {}
