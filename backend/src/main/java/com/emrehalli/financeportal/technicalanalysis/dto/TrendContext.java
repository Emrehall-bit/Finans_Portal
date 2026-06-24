package com.emrehalli.financeportal.technicalanalysis.dto;

import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;

public record TrendContext(
        TrendDirection shortTermTrend,
        TrendDirection selectedRangeTrend,
        TrendDirection maTrend,
        String rangeLabel,
        int dataPoints,
        boolean insufficientData
) {}
