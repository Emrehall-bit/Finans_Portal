package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NewsSyncResponseDto {

    private String provider;
    private Boolean enabled;
    private Integer feedUrlCount;
    private Integer fetched;
    private Integer fetchedFromFeed;
    private Integer fetchedFromApi;
    private Integer canonicalResolved;
    private Integer canonicalFailed;
    private Integer extractedFullContent;
    private Integer skippedFullContentNotAvailable;
    private Integer skippedCanonicalNotResolved;
    private Integer skippedByRelevance;
    private Integer duplicateSkipped;
    private Integer apiQuotaLeft;
    private Integer apiQuotaUsed;
    private Integer apiQuotaRequest;
    private Boolean startupSync;
    private String errorMessage;
    private List<String> lastErrors;
    private int fetchedCount;
    private int validCount;
    private int invalidCount;
    private int duplicateCount;
    private int existingCount;
    private int savedCount;
    private double parseSuccessRatio;
}
