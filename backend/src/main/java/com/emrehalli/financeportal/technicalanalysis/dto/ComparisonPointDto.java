package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComparisonPointDto(
        LocalDate date,
        BigDecimal close,
        BigDecimal normalizedValue
) {
}
