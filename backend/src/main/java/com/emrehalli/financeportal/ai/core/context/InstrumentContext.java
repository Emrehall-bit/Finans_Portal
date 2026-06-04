package com.emrehalli.financeportal.ai.core.context;

import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse;

/**
 * Backend-enriched instrument context: raw AiContext + pre-fetched analysis summaries.
 */
public record InstrumentContext(
        String symbol,
        String instrumentType,
        AiTechnicalAnalysisResponse technicalSummary,
        AiFundamentalAnalysisResponse fundamentalSummary
) {}




