package com.emrehalli.financeportal.ai.news;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;

import java.util.List;

public record NewsImpactResponse(
        String newsId,
        String summary,
        String marketImpact,
        List<String> affectedSectors,
        String sentiment,
        String riskLevel,
        List<String> highlights,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {}




