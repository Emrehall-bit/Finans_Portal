package com.emrehalli.financeportal.technicalanalysis.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface HistoricalPriceReader {

    List<HistoricalPricePoint> read(String symbol, LocalDate from, LocalDate to);
}

record HistoricalPricePoint(
        String symbol,
        LocalDate date,
        BigDecimal close
) {
}




