package com.emrehalli.financeportal.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformanceResponse(
        List<PortfolioPerformancePointDto> points,
        LocalDate fromDate,
        LocalDate toDate,
        boolean approximate
) {
    public PortfolioPerformanceResponse {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
