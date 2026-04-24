package com.emrehalli.financeportal.market.service.model;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryRecord(
        String symbol,
        String displayName,
        InstrumentType instrumentType,
        DataSource source,
        LocalDate priceDate,
        BigDecimal closePrice,
        String currency
) {
}
