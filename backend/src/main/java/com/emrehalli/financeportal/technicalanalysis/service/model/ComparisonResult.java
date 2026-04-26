package com.emrehalli.financeportal.technicalanalysis.service.model;

import java.time.LocalDate;
import java.util.List;

public record ComparisonResult(
        LocalDate from,
        LocalDate to,
        List<ComparisonSeries> series
) {
}
