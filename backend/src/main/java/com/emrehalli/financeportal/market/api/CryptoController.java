package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.CryptoService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
    private final ObservationRegistry observationRegistry;

    @GetMapping
    public ApiResponse<List<MarketQueryService.MarketSnapshot>> getAll() {
        Observation obs = Observation.createNotStarted("CryptoController.getCryptoMarkets", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/crypto")
                .lowCardinalityKeyValue("market.type", "CRYPTO")
                .start();
        try {
            List<MarketQueryService.MarketSnapshot> data = cryptoService.getAll();
            obs.highCardinalityKeyValue("result.count", String.valueOf(data.size()));
            return ApiResponse.<List<MarketQueryService.MarketSnapshot>>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .dataDate(resolveListDataDate(data))
                    .build();
        } catch (Exception ex) {
            obs.error(ex);
            throw ex;
        } finally {
            obs.stop();
        }
    }

    @GetMapping("/{symbol}")
    public ApiResponse<MarketQueryService.MarketSnapshot> getBySymbol(@PathVariable String symbol) {
        Observation obs = Observation.createNotStarted("CryptoController.getCryptoBySymbol", observationRegistry)
                .lowCardinalityKeyValue("endpoint", "/api/v1/markets/crypto/{symbol}")
                .lowCardinalityKeyValue("market.type", "CRYPTO")
                .start();
        try {
            MarketQueryService.MarketSnapshot data = cryptoService.getBySymbol(symbol);
            obs.highCardinalityKeyValue("symbol", symbol);
            obs.highCardinalityKeyValue("result.count", data != null ? "1" : "0");
            return ApiResponse.<MarketQueryService.MarketSnapshot>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .dataDate(data != null ? data.fetchedAt() : null)
                    .build();
        } catch (Exception ex) {
            obs.error(ex);
            throw ex;
        } finally {
            obs.stop();
        }
    }

    private LocalDateTime resolveListDataDate(List<MarketQueryService.MarketSnapshot> snapshots) {
        return snapshots.stream()
                .map(MarketQueryService.MarketSnapshot::fetchedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}




