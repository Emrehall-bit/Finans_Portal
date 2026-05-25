package com.emrehalli.financeportal.company.kap.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BackfillPairResultDto {
    private int year;
    private int period;
    private String reportType;
    private String status;            // FETCHED | SKIPPED | FAILED
    private String errorMessage;
    private int xlsxByteSize;
    private List<String> workbookSheetNames;
    private String rowPreviewFirst20;
    private int matchedItems;
    private int savedValues;
    private List<String> matchedLabels;
    private Integer firstMatchedRow;
    private String rowDumpIfEmpty;
}



