package com.emrehalli.financeportal.technicalanalysis.dto;

import java.util.List;

public record ComparisonSeriesDto(
        String symbol,
        List<ComparisonPointDto> points
) {
}
