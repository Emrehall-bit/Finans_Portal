package com.emrehalli.financeportal.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TechnicalFullResponse {
    private String symbol;
    private String timeframe;
    private List<OhlcvPoint> prices;
    private Map<String, Object> indicators;
}
