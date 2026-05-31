package com.emrehalli.financeportal.technicalanalysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinancialDataResponse {
    private Long id;
    private String period;
    private String periodType;
    private BigDecimal revenue;
    private BigDecimal grossProfit;
    private BigDecimal netIncome;
    private BigDecimal totalAssets;
    private BigDecimal totalEquity;
    private BigDecimal totalLiabilities;
    private BigDecimal currentAssets;
    private BigDecimal currentLiabilities;
    private BigDecimal operatingCashFlow;
}

