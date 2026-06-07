package com.emrehalli.financeportal.market.service;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class MarketScreenCriteria {
    private String type;
    private String source;
    private String q;
    private List<String> symbols;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minChangePercent;
    private BigDecimal maxChangePercent;
    private String sector;
    private String market;
    private BigDecimal minPe;
    private BigDecimal maxPe;
    private BigDecimal minPb;
    private BigDecimal maxPb;
    private BigDecimal minRoe;
    private BigDecimal minRoa;
    private BigDecimal maxDebtToEquity;
    private BigDecimal minRevenueGrowth;
    private BigDecimal minNetProfitGrowth;
    private Boolean onlyWithFundamentals;
    private String bistTier;
    private String stockSector;
    private int page;
    private int size;
    private String sort;
}
