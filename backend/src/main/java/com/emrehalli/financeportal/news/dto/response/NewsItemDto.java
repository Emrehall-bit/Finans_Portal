package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsItemDto {

    private String externalId;
    private String title;
    private String summary;
    private String source;
    private String provider;
    private String language;
    private String regionScope;
    private String category;
    private String relatedSymbol;
    private String url;
    private String imageUrl;
    private LocalDateTime publishedAt;
    private LocalDateTime contentEnrichedAt;
    private String qualityStatus;
    private Boolean isKapDisclosure;
    private String disclosureType;
}



