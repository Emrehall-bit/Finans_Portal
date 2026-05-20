package com.emrehalli.financeportal.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioRiskMetricDto(
        String label,
        int score,
        BigDecimal rawValue,
        String rawValueDisplay,
        String methodology
) {
}
