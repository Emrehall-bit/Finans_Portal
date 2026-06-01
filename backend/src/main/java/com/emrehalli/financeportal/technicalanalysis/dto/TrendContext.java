package com.emrehalli.financeportal.technicalanalysis.dto;

import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;

/**
 * Tek trendDirection değerinin yanıltıcı olabileceği uzun aralıklarda (MAX, 1Y)
 * ek trend bağlamı sunar. API contract kırılmaz; mevcut trendDirection alanı korunur.
 */
public record TrendContext(
        TrendDirection shortTermTrend,
        TrendDirection selectedRangeTrend,
        TrendDirection maTrend,
        String rangeLabel,
        int dataPoints,
        boolean insufficientData
) {}
