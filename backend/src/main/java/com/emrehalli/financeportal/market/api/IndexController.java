package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.service.IndexService;
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
 * REST controller for global stock index data.
 */
@RestController
@RequestMapping("/api/v1/markets/indexes")
@RequiredArgsConstructor
@Tag(name = "Endeksler", description = "Küresel borsa endeks verileri")
public class IndexController {

    private final IndexService indexService;
    private final ObservationRegistry observationRegistry;

    @Operation(summary = "Endeks verilerini listele")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Endeks verileri başarıyla listelendi"))
    @GetMapping
    public ApiResponse<List<MarketQuoteResponse>> getAll() {
        Observation obs = Observation.createNotStarted("IndexController.getIndexMarkets", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/indexes")
                .lowCardinalityKeyValue("market.type", "INDEX")
                .start();
        try {
            List<MarketQuoteResponse> data = indexService.getAll();
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

