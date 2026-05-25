package com.emrehalli.financeportal.company.kap.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FinancialBackfillResponse {
    private String tickerCode;
    private List<Integer> processedYears;
    private List<String> processedPeriods;
    private int processedPairs;
    private int fetchedPairs;
    private int skippedPairs;
    private int failedPairs;
    private int parsedRows;
    private int xlsxFetched;
    private int xlsxByteSize;
    private List<String> sheetNames;
    private int matchedItemCount;
    private List<String> unmatchedLabels;
    private int savedReports;
    private int savedValues;
    private int updatedValues;
    private List<BackfillPairResultDto> pairResults;
    private String message;
}



