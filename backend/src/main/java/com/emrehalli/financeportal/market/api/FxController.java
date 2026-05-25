package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.service.FxService;
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

    @GetMapping
    public ApiResponse<List<FxRateResponse>> getAll(@RequestParam(required = false) SourceName source) {
        List<FxRateResponse> data = source != null ? fxService.getBySource(source) : fxService.getAll();
        return ApiResponse.<List<FxRateResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(data))
                .build();
    }

    @GetMapping("/{code}")
    public ApiResponse<List<FxRateResponse>> getByCode(@PathVariable String code) {
        List<FxRateResponse> data = fxService.getByCode(code);
        return ApiResponse.<List<FxRateResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(data))
                .build();
    }

    private LocalDateTime resolveDataDate(List<FxRateResponse> responses) {
        return responses.stream()
                .map(FxRateResponse::getPriceTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}



