package com.emrehalli.financeportal.technicalanalysis.dto;

import java.time.LocalDate;
import java.util.List;

public record ComparisonResponse(
        LocalDate from,
        LocalDate to,
        List<ComparisonSeriesDto> series
) {
}
