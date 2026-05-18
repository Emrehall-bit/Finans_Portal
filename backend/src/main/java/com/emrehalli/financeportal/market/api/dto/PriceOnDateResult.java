package com.emrehalli.financeportal.market.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceOnDateResult(String symbol, LocalDate date, BigDecimal price) {
}
