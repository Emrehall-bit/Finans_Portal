package com.emrehalli.financeportal.market.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class MarketScreenItemResponse {
    private String symbol;
    private String name;
    private String type;
    private String source;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private BigDecimal buyChangePercent;
    private BigDecimal sellChangePercent;
    private BigDecimal volume;
    private BigDecimal lastPrice;
    private BigDecimal changePercent;
    private String sector;
    private String market;
    private BigDecimal marketCap;
    private BigDecimal peRatio;
    private BigDecimal pbRatio;
    private BigDecimal roe;
    private BigDecimal roa;
    private BigDecimal netMargin;
    private BigDecimal debtToEquity;
    private BigDecimal revenueGrowth;
    private BigDecimal netProfitGrowth;
    private OffsetDateTime calculatedAt;
    private LocalDateTime dataTimestamp;
    private String bistTier;
    private String stockSector;
}

