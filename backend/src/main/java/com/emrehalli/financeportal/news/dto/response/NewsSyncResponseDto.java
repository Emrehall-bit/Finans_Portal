package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsSyncResponseDto {

    private String provider;
    private int fetchedCount;
    private int validCount;
    private int invalidCount;
    private int duplicateCount;
    private int existingCount;
    private int savedCount;
    private double parseSuccessRatio;
}



