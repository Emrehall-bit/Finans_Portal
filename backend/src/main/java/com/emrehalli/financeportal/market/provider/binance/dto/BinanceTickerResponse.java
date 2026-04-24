package com.emrehalli.financeportal.market.provider.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTickerResponse(
        String symbol,
        String lastPrice,
        String priceChangePercent,
        Long closeTime
) {
}
