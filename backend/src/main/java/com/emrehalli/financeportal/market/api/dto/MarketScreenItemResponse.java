package com.emrehalli.financeportal.market.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Builder(toBuilder = true)
@Schema(description = "Piyasa tarama sonuç satırı")
public class MarketScreenItemResponse {
    private String symbol;
    private String name;
    private String type;
    private String displayUnit;
    private String currency;
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
    private String fundType;
    private String riskDegeri;
    private BigDecimal getiri1a;
    private BigDecimal getiri3a;
    private BigDecimal getiri6a;
    private BigDecimal getiriYb;
    private BigDecimal getiri1y;
    private BigDecimal getiri3y;
    private BigDecimal getiri5y;
}
