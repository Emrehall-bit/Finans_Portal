package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ComparisonResponse(
        LocalDate from,
        LocalDate to,
        List<Series> series
) {

    public record Series(
            String symbol,
            List<Point> points
    ) {
    }

    public record Point(
            LocalDate date,
            BigDecimal close,
            BigDecimal normalizedValue
    ) {
    }
}
