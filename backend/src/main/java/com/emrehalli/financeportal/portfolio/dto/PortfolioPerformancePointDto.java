package com.emrehalli.financeportal.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioPerformancePointDto(
        LocalDate date,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal profitLoss,
        BigDecimal profitLossPercent,
        BigDecimal twrNormalized
) {
}




