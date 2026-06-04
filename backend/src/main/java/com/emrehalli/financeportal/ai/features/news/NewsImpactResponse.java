package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

import java.util.List;

public record NewsImpactResponse(
        String newsId,
        String financialContext,
        List<String> affectedAssets,
        String marketImplication,
        List<String> watchIndicators,
        String uncertainty,
        List<String> highlights,
        String shortTermImpact,
        String mediumTermImpact,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata,
        String summary,
        String marketImpact,
        List<String> affectedSectors
) {}
