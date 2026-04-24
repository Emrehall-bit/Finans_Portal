package com.emrehalli.financeportal.market.provider.evds.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvdsResponse(
        @JsonProperty("items")
        List<EvdsItem> items
) {
    public EvdsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
