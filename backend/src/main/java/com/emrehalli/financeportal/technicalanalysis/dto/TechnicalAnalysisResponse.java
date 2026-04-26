package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TechnicalAnalysisResponse(
        String symbol,
        LocalDate from,
        LocalDate to,
        BigDecimal latestPrice,
        String trendDirection,
        List<String> signals,
        List<IndicatorValueDto> indicatorValues,
        List<TechnicalAnalysisPointDto> points
) {
}
