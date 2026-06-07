package com.emrehalli.financeportal.technicalanalysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BenchmarkResponse(
        LocalDate from,
        LocalDate to,
        String baseCode,
        String benchmarkCode,
        String benchmarkType,
        LocalDate effectiveStart,
        LocalDate effectiveEnd,
        LocalDate benchmarkEffectiveEnd,
        BigDecimal baseReturn,
        BigDecimal benchmarkReturn,
        BigDecimal relativeReturn,
        BigDecimal realReturn,
        String status,
        List<ComparisonResponse.Series> series
) {
}
