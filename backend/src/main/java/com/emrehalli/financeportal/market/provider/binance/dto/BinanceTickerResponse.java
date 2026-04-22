package com.emrehalli.financeportal.market.provider.binance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinanceTickerResponse {

    private LocalDateTime fetchedAt;
    private List<BinanceTickerItem> items;
}
