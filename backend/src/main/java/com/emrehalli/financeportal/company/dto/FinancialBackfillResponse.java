package com.emrehalli.financeportal.company.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FinancialBackfillResponse {
    private String tickerCode;
    private List<Integer> processedYears;
    private List<String> processedPeriods;
    private int parsedRows;
    private int savedReports;
    private int savedValues;
    private int updatedValues;
    private String message;
}
