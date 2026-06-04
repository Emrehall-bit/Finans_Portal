package com.emrehalli.financeportal.ai.features.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardAiInputContext(
        BigDecimal avgMarketChange,
        int gainerCount,
        int loserCount,
        int totalQuotes,
        List<String> recentNewsTitles
) {}
