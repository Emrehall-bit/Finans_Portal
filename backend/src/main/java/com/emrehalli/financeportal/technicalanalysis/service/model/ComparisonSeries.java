package com.emrehalli.financeportal.technicalanalysis.service.model;

import java.util.List;

public record ComparisonSeries(
        String symbol,
        List<ComparisonPoint> points
) {
}
