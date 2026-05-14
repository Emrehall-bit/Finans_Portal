package com.emrehalli.financeportal.company.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinancialReportSyncResponse {

    private String tickerCode;
    private int discoveredReports;
    private int savedReports;
    private int duplicateSkipped;
    private int parsedReports;
    private int successCount;
    private int partialCount;
    private int failedCount;
    private String message;
}
