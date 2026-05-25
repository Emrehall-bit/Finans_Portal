package com.emrehalli.financeportal.company.kap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParseFailureDto {

    private Long reportId;
    private String sourceUrl;
    private Integer periodYear;
    private Integer periodQuarter;
    private String reason;
    private String contentType;
    private String fetchedTextPreview;
    private String selectedSheetName;
    private String workbookSheetNames;
    private String rowPreviewFirst20;
}



