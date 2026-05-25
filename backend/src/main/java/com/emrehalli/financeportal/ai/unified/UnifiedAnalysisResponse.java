package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;

import java.util.List;

public record UnifiedAnalysisResponse(
        String symbol,
        String summary,
        List<String> highlights,
        List<String> risks,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {}



