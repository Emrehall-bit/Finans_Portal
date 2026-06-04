package com.emrehalli.financeportal.ai.features.unified;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

import java.util.List;

public record UnifiedAnalysisResponse(
        String symbol,
        String summary,
        List<String> highlights,
        List<String> risks,
        String alignment,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {}
