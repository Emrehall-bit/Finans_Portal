package com.emrehalli.financeportal.technicalanalysis.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundamentalHistoryPoint {
    private String period;
    private BigDecimal revenue;
    private BigDecimal netIncome;
    private BigDecimal grossMargin;
    private BigDecimal netMargin;
    private BigDecimal roe;
    private BigDecimal peRatio;
}

