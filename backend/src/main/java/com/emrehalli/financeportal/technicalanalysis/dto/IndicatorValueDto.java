package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;

public record IndicatorValueDto(
        String indicator,
        BigDecimal value
) {
}
