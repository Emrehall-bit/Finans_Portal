package com.emrehalli.financeportal.technicalanalysis.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndicatorConfigResponse {
    private Long id;
    private String indicatorType;
    private Map<String, Object> parameters;
    private boolean isActive;
    private OffsetDateTime createdAt;
}

