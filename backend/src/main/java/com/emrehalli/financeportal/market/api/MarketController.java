package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.api.dto.MarketAggregateResponse;
import com.emrehalli.financeportal.market.service.CryptoService;
import com.emrehalli.financeportal.market.service.FxService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for aggregate market data.
 */
@RestController
@RequestMapping("/api/v1/markets")
@AllArgsConstructor
public class MarketController {

    private final FxService fxService;
    private final CryptoService cryptoService;

    @GetMapping
    public ApiResponse<MarketAggregateResponse> getAllMarkets(@RequestParam(name = "type", required = false) String type) {
        boolean macroIndicatorRequest = isMacroIndicatorRequest(type);
        List<FxRateResponse> fx = macroIndicatorRequest ? List.of() : fxService.getAll();
        List<MarketQueryService.MarketSnapshot> cryptoSnapshots = macroIndicatorRequest ? List.of() : cryptoService.getAll();
        List<Object> crypto = new ArrayList<>(cryptoSnapshots);
        MarketAggregateResponse data = MarketAggregateResponse.builder()
                .fx(fx)
                .crypto(crypto)
                .stocks(List.of())
                .funds(List.of())
                .futures(List.of())
                .bonds(List.of())
                .build();

        return ApiResponse.<MarketAggregateResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(fx, cryptoSnapshots))
                .build();
    }

    private boolean isMacroIndicatorRequest(String type) {
        return type != null && "MACRO_INDICATOR".equalsIgnoreCase(type.trim());
    }

    private LocalDateTime resolveDataDate(List<FxRateResponse> responses,
                                          List<MarketQueryService.MarketSnapshot> cryptoSnapshots) {
        LocalDateTime fxLatest = responses.stream()
                .map(FxRateResponse::getPriceTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime cryptoLatest = cryptoSnapshots.stream()
                .map(MarketQueryService.MarketSnapshot::fetchedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (fxLatest == null) {
            return cryptoLatest;
        }
        if (cryptoLatest == null) {
            return fxLatest;
        }
        return fxLatest.isAfter(cryptoLatest) ? fxLatest : cryptoLatest;
    }
}
