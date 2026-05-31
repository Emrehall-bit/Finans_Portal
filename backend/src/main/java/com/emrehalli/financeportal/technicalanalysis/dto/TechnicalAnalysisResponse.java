package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TechnicalAnalysisResponse(
        String symbol,
        LocalDate from,
        LocalDate to,
        BigDecimal latestPrice,
        String analysisStatus,
        String message,
        String trendDirection,
        List<String> signals,
        List<IndicatorValue> indicatorValues,
        List<Point> points
) {

    public record Point(
            LocalDate date,
            BigDecimal close,
            BigDecimal sma7,
            BigDecimal sma20,
            BigDecimal sma50,
            BigDecimal rsi14
    ) {
    }

    public record IndicatorValue(
            String indicator,
            BigDecimal value
    ) {
    }
}
