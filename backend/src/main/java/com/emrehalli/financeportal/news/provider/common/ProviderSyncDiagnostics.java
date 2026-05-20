package com.emrehalli.financeportal.news.provider.common;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProviderSyncDiagnostics {

    private String provider;
    private boolean enabled;
    private int feedUrlCount;
    private int fetched;
    private int fetchedFromFeed;
    private int fetchedFromApi;
    private int canonicalResolved;
    private int canonicalFailed;
    private int extractedFullContent;
    private int skippedFullContentNotAvailable;
    private int skippedCanonicalNotResolved;
    private int skippedByRelevance;
    private Integer apiQuotaLeft;
    private Integer apiQuotaUsed;
    private Integer apiQuotaRequest;
    private Integer apiReturnedCount;
    private Integer fullContentEligibleCount;
    private Integer skippedTooOld;
    private Integer skippedBlockedSource;
    private String firstReturnedTitle;
    private LocalDateTime firstReturnedPublishedAt;
    private String errorMessage;
    private List<String> lastErrors;

    public static ProviderSyncDiagnostics empty(String provider) {
        return ProviderSyncDiagnostics.builder()
                .provider(provider)
                .enabled(true)
                .feedUrlCount(0)
                .fetched(0)
                .fetchedFromFeed(0)
                .fetchedFromApi(0)
                .canonicalResolved(0)
                .canonicalFailed(0)
                .extractedFullContent(0)
                .skippedFullContentNotAvailable(0)
                .skippedCanonicalNotResolved(0)
                .skippedByRelevance(0)
                .apiQuotaLeft(null)
                .apiQuotaUsed(null)
                .apiQuotaRequest(null)
                .apiReturnedCount(0)
                .fullContentEligibleCount(0)
                .skippedTooOld(0)
                .skippedBlockedSource(0)
                .firstReturnedTitle(null)
                .firstReturnedPublishedAt(null)
                .errorMessage(null)
                .lastErrors(List.of())
                .build();
    }
}
