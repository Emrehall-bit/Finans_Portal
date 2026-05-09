package com.emrehalli.financeportal.market.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregate market response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAggregateResponse {

    private List<FxRateResponse> fx;
    private List<Object> crypto;
    private List<Object> stocks;
    private List<Object> funds;
    private List<Object> futures;
    private List<Object> bonds;
}
