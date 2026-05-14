package com.emrehalli.financeportal.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyDisclosureSyncResponse {

    private String tickerCode;
    private int fetchedCount;
    private int savedCount;
    private int updatedCount;
    private int duplicateSkippedCount;
    private int failedCount;
    private String message;
    private List<DisclosureFailedItemDto> failedItems;
}
