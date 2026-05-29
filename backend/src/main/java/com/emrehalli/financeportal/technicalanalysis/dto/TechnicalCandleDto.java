package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;

public record TechnicalCandleDto(
        long timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal sma7,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal rsi14
) {
}

