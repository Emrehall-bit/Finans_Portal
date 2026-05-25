package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.CryptoService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for crypto market data.
 */
@RestController
@RequestMapping({"/api/v1/markets/crypto", "/api/v1/markets/type/CRYPTO"})
@AllArgsConstructor
public class CryptoController {

    private final CryptoService cryptoService;

    @GetMapping
    public ApiResponse<List<MarketQueryService.MarketSnapshot>> getAll() {
        List<MarketQueryService.MarketSnapshot> data = cryptoService.getAll();
        return ApiResponse.<List<MarketQueryService.MarketSnapshot>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveListDataDate(data))
                .build();
    }

    @GetMapping("/{symbol}")
    public ApiResponse<MarketQueryService.MarketSnapshot> getBySymbol(@PathVariable String symbol) {
        MarketQueryService.MarketSnapshot data = cryptoService.getBySymbol(symbol);
        return ApiResponse.<MarketQueryService.MarketSnapshot>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.fetchedAt() : null)
                .build();
    }

    private LocalDateTime resolveListDataDate(List<MarketQueryService.MarketSnapshot> snapshots) {
        return snapshots.stream()
                .map(MarketQueryService.MarketSnapshot::fetchedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}



