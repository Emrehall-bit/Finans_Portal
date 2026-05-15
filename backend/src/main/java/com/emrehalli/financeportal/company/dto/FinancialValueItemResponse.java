package com.emrehalli.financeportal.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinancialValueItemResponse {

    private String itemKey;
    private String rawLabel;
    private BigDecimal value;
    private String currency;
    private Integer unitMultiplier;
    private boolean currentPeriod;
    private boolean comparisonAvailable;
    private String comparisonPeriod;
    private BigDecimal changePercent;
}
