package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RelatedNewsItemDto(
        Long id,
        String title,
        String sourceName,
        String provider,
        String imageUrl,
        String category,
        LocalDateTime publishedAt,
        Integer importanceScore
) {
}




