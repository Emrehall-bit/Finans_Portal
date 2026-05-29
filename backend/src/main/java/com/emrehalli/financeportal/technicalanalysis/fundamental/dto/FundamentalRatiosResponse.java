package com.emrehalli.financeportal.technicalanalysis.fundamental.dto;

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

    // DeÄŸerleme
    private BigDecimal peRatio;
    private BigDecimal pbRatio;

    // KarlÄ±lÄ±k
    private BigDecimal grossMargin;
    private BigDecimal netMargin;
    private BigDecimal roe;
    private BigDecimal roa;

    // BÃ¼yÃ¼me
    private BigDecimal revenueGrowthYoy;
    private BigDecimal netIncomeGrowthYoy;
    private BigDecimal assetGrowthYoy;

    // BorÃ§/Risk
    private BigDecimal debtToEquity;
    private BigDecimal currentRatio;

    // Genel sinyal
    private String overallSignal;

    // Premium alanlar â€” null gelirse frontend blur gÃ¶sterir
    private BigDecimal grahamNumber;
    private Integer piotroskiScore;
    private BigDecimal altmanZScore;
    private Boolean premiumRequired;

    private OffsetDateTime calculatedAt;
}

