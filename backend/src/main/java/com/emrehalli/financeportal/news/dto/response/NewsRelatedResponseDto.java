package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record NewsRelatedResponseDto(
        List<RelatedInstrumentDto> relatedInstruments,
        List<RelatedNewsItemDto> relatedNews
) {
}




