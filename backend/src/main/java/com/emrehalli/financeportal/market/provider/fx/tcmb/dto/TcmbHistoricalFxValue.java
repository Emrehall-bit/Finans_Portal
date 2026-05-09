package com.emrehalli.financeportal.market.provider.fx.tcmb.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TcmbHistoricalFxValue(
        String instrumentCode,
        String seriesCode,
        LocalDate priceDate,
        BigDecimal priceValue
) {
}
