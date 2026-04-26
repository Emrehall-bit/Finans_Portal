package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TechnicalAnalysisPointDto(
        LocalDate date,
        BigDecimal close,
        BigDecimal sma7,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal rsi14
) {
}
