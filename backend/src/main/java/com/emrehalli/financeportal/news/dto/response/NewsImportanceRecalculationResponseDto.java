package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsImportanceRecalculationResponseDto {

    private int totalProcessed;
    private int updatedCount;
    private int minScore;
    private int maxScore;
    private double averageScore;
}
