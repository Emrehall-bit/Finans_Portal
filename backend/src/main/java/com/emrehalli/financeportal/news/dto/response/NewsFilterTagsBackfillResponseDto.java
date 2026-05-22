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
public class NewsFilterTagsBackfillResponseDto {

    private int processedCount;
    private int updatedCount;
    private int skippedKapCount;
    private int unchangedCount;
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
        private String oldTags;
        private String newTags;
    }
}
