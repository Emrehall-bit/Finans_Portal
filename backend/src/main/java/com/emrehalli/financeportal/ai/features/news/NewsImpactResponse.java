package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

import java.util.List;

public record NewsImpactResponse(
        String newsId,
        String summary,           // finansalContext içeriği; geriye dönük uyumluluk için korundu
        String marketImpact,      // shortTermImpact içeriği; geriye dönük uyumluluk için korundu
        String shortTermImpact,
        String mediumTermImpact,
        String uncertainty,
        List<String> affectedSectors,
        String sentiment,
        String riskLevel,
        List<String> highlights,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {}




