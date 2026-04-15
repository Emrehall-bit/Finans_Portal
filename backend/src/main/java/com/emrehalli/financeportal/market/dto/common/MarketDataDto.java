package com.emrehalli.financeportal.market.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private String name;
    private String price;
    private String source;
    private String lastUpdated;
}