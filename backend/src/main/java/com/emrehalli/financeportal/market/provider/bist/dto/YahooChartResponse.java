package com.emrehalli.financeportal.market.provider.bist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(
        Chart chart
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(
            List<Result> result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Meta meta,
            List<Long> timestamp,
            Indicators indicators
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            String symbol
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(
            List<Quote> quote
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            List<BigDecimal> close
    ) {
    }
}
