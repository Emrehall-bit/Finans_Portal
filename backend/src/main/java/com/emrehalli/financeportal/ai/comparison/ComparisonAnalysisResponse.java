package com.emrehalli.financeportal.ai.comparison;

import java.util.List;

public record ComparisonAnalysisResponse(
        String leftSymbol,
        String rightSymbol,
        String summary,
        String technicalComparison,
        String fundamentalComparison,
        String riskComparison,
        List<String> strengthsLeft,
        List<String> strengthsRight,
        List<String> weaknessesLeft,
        List<String> weaknessesRight,
        String finalComment,
        DataQuality dataQuality,
        String providerUsed,
        boolean fallbackUsed
) {
    public enum DataQuality {
        COMPLETE,
        PARTIAL,
        LIMITED
    }
}
