package com.emrehalli.financeportal.company.kap.dto;

import com.emrehalli.financeportal.company.domain.enums.ReportType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class KapFinancialReportDto {

    private Long companyId;
    private Integer periodYear;
    private Integer periodQuarter;
    private ReportType reportType;
    private String sourceUrl;
    private LocalDate publishedAt;
}



