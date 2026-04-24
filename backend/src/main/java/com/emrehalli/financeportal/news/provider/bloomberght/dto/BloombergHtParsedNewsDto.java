package com.emrehalli.financeportal.news.provider.bloomberght.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BloombergHtParsedNewsDto {

    private String title;
    private String summary;
    private String url;
    private String category;
    private LocalDateTime publishedAt;
}



