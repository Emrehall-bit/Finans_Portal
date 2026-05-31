package com.emrehalli.financeportal.technicalanalysis.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ComparisonResult(
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
