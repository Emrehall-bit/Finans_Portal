package com.emrehalli.financeportal.market.domain;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String symbol,
        String displayName,
        InstrumentType instrumentType,
        BigDecimal price,
        BigDecimal changeRate,
        String currency,
        DataSource source,
        Instant priceTime,
        Instant fetchedAt
) implements Serializable {
}