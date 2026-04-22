package com.emrehalli.financeportal.market.provider.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceTickerItem {

    private String symbol;
    private String lastPrice;
    private String priceChange;
    private String priceChangePercent;
    private Long closeTime;
}
