package com.emrehalli.financeportal.ai.insight;

import java.util.List;

public record TechnicalInsight(
        List<FinancialInsight> signals,
        String trendSummary,
        String momentumSummary
) {}




