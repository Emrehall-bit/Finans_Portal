package com.emrehalli.financeportal.market.dto.common;

import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private String name;
    private InstrumentType instrumentType;
    private BigDecimal price;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private String currency;
    private LocalDateTime priceTime;
    private LocalDateTime fetchedAt;
    private String source;
    private MarketDataFreshness freshness;
}
