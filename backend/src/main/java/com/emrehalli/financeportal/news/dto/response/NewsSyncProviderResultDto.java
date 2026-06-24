package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsSyncProviderResultDto {

    private String provider;
    private boolean success;
    private int fetchedCount;
    private int savedCount;
    private int skippedCount;
    private int duplicateCount;
    private int timeoutCount;
    private int parseErrorCount;
    private String errorMessage;
}

