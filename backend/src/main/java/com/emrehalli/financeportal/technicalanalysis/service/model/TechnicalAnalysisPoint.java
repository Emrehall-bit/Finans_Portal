package com.emrehalli.financeportal.technicalanalysis.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TechnicalAnalysisPoint(
        LocalDate date,
        BigDecimal close,
        BigDecimal sma7,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal rsi14
) {
}
