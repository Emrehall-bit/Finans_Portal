package com.emrehalli.financeportal.ai.unified;

import java.util.List;

/**
 * Assembled context for the unified AI prompt.
 * All fields are pre-interpreted natural language strings â€” no raw numbers.
 */
public record UnifiedAnalysisContext(
        String symbol,
        // Technical
        String technicalSummary,
        String trendObservation,
        String momentumObservation,
        String technicalSignal,
        // Fundamental (absent for non-STOCK instruments)
        boolean hasFundamentals,
        String fundamentalSummary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> fundamentalRisks,
        String growthObservation,
        String financialHealthLabel,
        // Derived reasoning â€” describes tech vs. fundamental alignment
        String conflictNote
) {}




