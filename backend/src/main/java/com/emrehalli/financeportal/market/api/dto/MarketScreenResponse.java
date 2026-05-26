package com.emrehalli.financeportal.market.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MarketScreenResponse {
    private List<MarketScreenItemResponse> content;
    private long totalElements;
    private int page;
    private int size;
}
