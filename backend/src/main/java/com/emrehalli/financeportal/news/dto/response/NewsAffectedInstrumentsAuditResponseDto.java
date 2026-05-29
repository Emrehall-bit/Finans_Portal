package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record NewsAffectedInstrumentsAuditResponseDto(
        int checkedCount,
        int suspiciousCount,
        int emptyCount,
        int highConfidenceCount,
        int mediumConfidenceCount,
        int lowConfidenceCount,
        List<SuspiciousItemDto> suspiciousItems
) {

    @Builder
    public record SuspiciousItemDto(
            Long newsId,
            String title,
            String category,
            String filterTags,
            List<String> affectedSymbols,
            List<String> reasons,
            String suspiciousReason
    ) {
    }
}




