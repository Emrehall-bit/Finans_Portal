package com.emrehalli.financeportal.market.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Konsolide piyasa verisi yanıt modeli")
public class MarketAggregateResponse {

    @Schema(description = "Döviz kurları")
    private List<FxRateResponse> fx;

    @Schema(description = "Kripto para verileri")
    private List<Object> crypto;

    @Schema(description = "Hisse senedi verileri")
    private List<Object> stocks;

    @Schema(description = "Fon verileri")
    private List<Object> funds;

    @Schema(description = "Vadeli işlem verileri")
    private List<Object> futures;

    @Schema(description = "Tahvil verileri")
    private List<Object> bonds;
}

