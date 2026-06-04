package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.features.fundamental.FinancialInsight;

import java.util.List;

public record TechnicalInsight(
        List<FinancialInsight> signals,
        String trendSummary,
        String momentumSummary
) {}




