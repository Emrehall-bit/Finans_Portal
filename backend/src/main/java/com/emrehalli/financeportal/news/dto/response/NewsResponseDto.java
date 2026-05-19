package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsResponseDto {

    private Long id;
    private String externalId;
    private String title;
    private String summary;
    private String contentPreview;
    private String source;
    private String sourceName;
    private String provider;
    private String language;
    private String regionScope;
    private String category;
    private String relatedSymbol;
    private String url;
    private String sourceUrl;
    private String imageUrl;
    private LocalDateTime publishedAt;
    private Integer importanceScore;
    private String qualityStatus;
    private Boolean isKapDisclosure;
    private String disclosureType;
}



