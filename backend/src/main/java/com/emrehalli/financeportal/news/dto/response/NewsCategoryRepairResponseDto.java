package com.emrehalli.financeportal.news.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsCategoryRepairResponseDto {

    private int processedCount;
    private int changedCategoryCount;
    private int unchangedCount;
    private int skippedKapCount;
    private List<SampleChangeDto> sampleChanges;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SampleChangeDto {
        private Long id;
        private String title;
        private String oldCategory;
        private String newCategory;
        private String reason;
    }
}
