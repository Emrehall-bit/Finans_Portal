package com.emrehalli.financeportal.markettape.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateMarketTapeConfigRequest(
        @NotNull List<String> symbols
) {
}
