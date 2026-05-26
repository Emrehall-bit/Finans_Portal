package com.emrehalli.financeportal.company.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyRatioCalculationResponse {

    private String tickerCode;
    private String reportPeriod;
    private BigDecimal priceAtCalc;
    private boolean calculated;
    private String failedReason;
    private String missingReason;
    private String peStatus;
    private String peMissingReason;
    private String pbStatus;
    private String pbMissingReason;
    private BigDecimal peRatio;
    private BigDecimal pbRatio;
    private BigDecimal debtToEquity;
    private BigDecimal grossMargin;
    private BigDecimal netMargin;
    private BigDecimal roe;
    private BigDecimal roa;
    private BigDecimal revenueGrowth;
    private String revenueGrowthLabel;
    private BigDecimal netProfitGrowth;
    private String netProfitGrowthLabel;
    private BigDecimal assetGrowth;
    private String assetGrowthLabel;
    private String healthLabel;
    private OffsetDateTime calculatedAt;
}



