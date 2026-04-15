package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsSyncResponseDto {

    private String provider;
    private int fetchedCount;
    private int savedCount;
}
