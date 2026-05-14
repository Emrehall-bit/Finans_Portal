package com.emrehalli.financeportal.company.dto;

import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyFinancialReportResponse {

    private Long reportId;
    private Integer periodYear;
    private Integer periodQuarter;
    private ReportType reportType;
    private LocalDate publishedAt;
    private ParseStatus parseStatus;
    private String sourceUrl;
    private OffsetDateTime lastCheckedAt;
    private List<FinancialValueItemResponse> values;
}
