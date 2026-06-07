package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.service.FxService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for FX market data.
 */
@RestController
@RequestMapping("/api/v1/markets/fx")
@AllArgsConstructor
public class FxController {

    private final FxService fxService;
    private final ObservationRegistry observationRegistry;

    @GetMapping
    public ApiResponse<List<FxRateResponse>> getAll(@RequestParam(required = false) SourceName source) {
        Observation obs = Observation.createNotStarted("FxController.getFxMarkets", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/fx")
                .lowCardinalityKeyValue("market.type", "FX")
                .start();
        try {
            List<FxRateResponse> data = source != null ? fxService.getBySource(source) : fxService.getAll();
            obs.highCardinalityKeyValue("result.count", String.valueOf(data.size()));
            if (source != null) {
                obs.highCardinalityKeyValue("source", source.name());
            }
            return ApiResponse.<List<FxRateResponse>>builder()
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

    @GetMapping("/{code}")
    public ApiResponse<List<FxRateResponse>> getByCode(@PathVariable String code) {
        Observation obs = Observation.createNotStarted("FxController.getFxByCode", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/fx/{code}")
                .lowCardinalityKeyValue("market.type", "FX")
                .start();
        try {
            List<FxRateResponse> data = fxService.getByCode(code);
            obs.highCardinalityKeyValue("fx.code", code);
            obs.highCardinalityKeyValue("result.count", String.valueOf(data.size()));
            return ApiResponse.<List<FxRateResponse>>builder()
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

    private LocalDateTime resolveDataDate(List<FxRateResponse> responses) {
        return responses.stream()
                .map(FxRateResponse::getPriceTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}




