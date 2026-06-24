package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.service.CommodityService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for commodity market data (gold, silver, oil, gas).
 */
@RestController
@RequestMapping("/api/v1/markets/commodities")
@RequiredArgsConstructor
@Tag(name = "Emtialar", description = "Emtia piyasa verileri (altın, gümüş, petrol, doğalgaz)")
public class CommodityController {

    private final CommodityService commodityService;
    private final ObservationRegistry observationRegistry;

    @Operation(summary = "Emtia fiyatlarını listele")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Emtia fiyatları başarıyla listelendi"))
    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getAll() {
        Observation obs = Observation.createNotStarted("CommodityController.getCommodityMarkets", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/commodities")
                .lowCardinalityKeyValue("market.type", "COMMODITY")
                .start();
        try {
            List<MarketQuoteResponse> data = commodityService.getAll();
            obs.highCardinalityKeyValue("result.count", String.valueOf(data.size()));
            return ApiResponse.<List<MarketQuoteResponse>>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .dataDate(resolveDataDate(data))
                    .build();
        } catch (Exception ex) {
            obs.error(ex);
            throw ex;
        } finally {
            obs.stop();
        }
    }

    private LocalDateTime resolveDataDate(List<MarketQuoteResponse> items) {
        return items.stream()
                .map(MarketQuoteResponse::updatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}

