package com.emrehalli.financeportal.technicalanalysis.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPricePoint(
        String symbol,
        LocalDate date,
        BigDecimal close
) {
}
