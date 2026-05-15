package com.emrehalli.financeportal.ai.context;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;

/**
 * Backend-enriched instrument context: raw AiContext + pre-fetched analysis summaries.
 */
public record InstrumentContext(
        String symbol,
        String instrumentType,
        AiTechnicalAnalysisResponse technicalSummary,
        AiFundamentalAnalysisResponse fundamentalSummary
) {}
