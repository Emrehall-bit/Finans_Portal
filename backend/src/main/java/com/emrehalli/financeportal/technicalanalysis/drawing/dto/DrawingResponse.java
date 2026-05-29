package com.emrehalli.financeportal.technicalanalysis.drawing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrawingResponse {
    private Long id;
    private Long instrumentId;
    private String timeframe;
    private String drawingType;
    private List<Map<String, Object>> points;
    private Map<String, Object> style;
    private String label;
    private boolean isAlertLinked;
    private Long linkedAlertId;
    private boolean isPremiumFeature;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

