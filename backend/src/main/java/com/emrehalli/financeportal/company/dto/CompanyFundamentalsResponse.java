package com.emrehalli.financeportal.company.dto;

import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyFundamentalsResponse {

    private String tickerCode;
    private String companyName;
    private String sector;
    private String market;

    private String latestReportPeriod;
    private ReportType latestReportType;
    private LocalDate latestReportPublishedAt;
    private ParseStatus parseStatus;

    private BigDecimal priceAtCalc;
    private OffsetDateTime calculatedAt;

    private BigDecimal marketCap;
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

    private String message;
}
