package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RssProviderFetchResult {

    private List<NewsItemDto> items;
    private int feedUrlCount;
    private int fetchedFromFeed;
    private int canonicalResolved;
    private int canonicalFailed;
    private int extractedFullContent;
    private int skippedFullContentNotAvailable;
    private int skippedCanonicalNotResolved;
    private int skippedByRelevance;
    private String errorMessage;
    private List<String> lastErrors;
}




