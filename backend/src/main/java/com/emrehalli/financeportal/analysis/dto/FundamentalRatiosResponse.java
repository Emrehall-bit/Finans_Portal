package com.emrehalli.financeportal.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundamentalRatiosResponse {

    private String period;
    private BigDecimal calculationPrice;

    // Değerleme
    private BigDecimal peRatio;
    private BigDecimal pbRatio;

    // Karlılık
    private BigDecimal grossMargin;
    private BigDecimal netMargin;
    private BigDecimal roe;
    private BigDecimal roa;

    // Büyüme
    private BigDecimal revenueGrowthYoy;
    private BigDecimal netIncomeGrowthYoy;
    private BigDecimal assetGrowthYoy;

    // Borç/Risk
    private BigDecimal debtToEquity;
    private BigDecimal currentRatio;

    // Genel sinyal
    private String overallSignal;

    // Premium alanlar — null gelirse frontend blur gösterir
    private BigDecimal grahamNumber;
    private Integer piotroskiScore;
    private BigDecimal altmanZScore;
    private Boolean premiumRequired;

    private OffsetDateTime calculatedAt;
}
