package com.emrehalli.financeportal.company.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyDisclosureSyncResponse {

    private String tickerCode;
    private int fetchedCount;
    private int savedCount;
    private int duplicateSkippedCount;
    private int failedCount;
    private String message;
}
