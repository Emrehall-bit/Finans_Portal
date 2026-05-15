package com.emrehalli.financeportal.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
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
    private OffsetDateTime oldestPublishedAt;
    private OffsetDateTime newestPublishedAt;
    private String message;
    private List<DisclosureFailedItemDto> failedItems;

    @JsonProperty("fetched")
    public int getFetched() {
        return fetchedCount;
    }

    @JsonProperty("saved")
    public int getSaved() {
        return savedCount;
    }

    @JsonProperty("updated")
    public int getUpdated() {
        return updatedCount;
    }

    @JsonProperty("duplicates")
    public int getDuplicates() {
        return duplicateSkippedCount;
    }

    @JsonProperty("failed")
    public int getFailed() {
        return failedCount;
    }
}
