package com.emrehalli.financeportal.market.provider.yahoo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YahooFinanceQuoteResponse {

    private LocalDateTime fetchedAt;
    private List<Map<String, Object>> items;
}
