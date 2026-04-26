package com.emrehalli.financeportal.technicalanalysis.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComparisonPoint(
        LocalDate date,
        BigDecimal close,
        BigDecimal normalizedValue
) {
}
