package com.emrehalli.financeportal.company.kap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinancialReportSyncResponse {

    private String tickerCode;
    private int discoveredReports;
    private int savedReports;
    private int duplicateSkipped;
    private int parsedReports;
    private int successCount;
    private int partialCount;
    private int failedCount;
    private int matchedItemCount;
    private int savedValueCount;
    private int reparsedCount;
    private int updatedValueCount;
    private String message;
    private List<SkippedReportReasonDto> skippedReasons;
    private List<ParseFailureDto> parseFailures;
}



