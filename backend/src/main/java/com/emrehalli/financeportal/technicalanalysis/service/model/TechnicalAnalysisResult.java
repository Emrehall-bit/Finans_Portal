package com.emrehalli.financeportal.technicalanalysis.service.model;

import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TechnicalAnalysisResult(
        String symbol,
        LocalDate from,
        LocalDate to,
        BigDecimal latestPrice,
        TrendDirection trendDirection,
        List<TechnicalSignal> signals,
        Map<IndicatorType, BigDecimal> indicatorValues,
        List<TechnicalAnalysisPoint> points
) {
}
