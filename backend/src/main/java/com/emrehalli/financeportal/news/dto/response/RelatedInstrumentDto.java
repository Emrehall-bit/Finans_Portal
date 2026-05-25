package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record RelatedInstrumentDto(
        String symbol,
        String name,
        String instrumentType,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        String relationType,
        String confidence,
        String reason
) {
}



