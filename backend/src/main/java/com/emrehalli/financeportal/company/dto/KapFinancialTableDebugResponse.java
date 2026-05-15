package com.emrehalli.financeportal.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KapFinancialTableDebugResponse {

    private boolean success;
    private Integer httpStatus;
    private String contentType;
    private Integer xlsxByteSize;
    private Map<String, Object> payload;
    private String errorBody;
    private String message;
}
