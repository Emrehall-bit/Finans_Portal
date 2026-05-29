package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsPurgeResponseDto {

    private final String provider;
    private final long deletedCount;
}




