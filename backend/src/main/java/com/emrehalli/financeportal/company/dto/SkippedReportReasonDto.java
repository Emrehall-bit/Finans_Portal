package com.emrehalli.financeportal.company.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkippedReportReasonDto {

    private String title;
    private String kapUrl;
    private String reason;
}
