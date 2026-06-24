package com.emrehalli.financeportal.company.dto.importcsv;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManualFinancialImportError {

    private Integer lineNumber;

    private String tickerCode;

    private Integer periodYear;

    private String reportType;

    private String itemKey;

    private String fieldName;

    private String rawValue;

    private String message;
}

