package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.api.dto.MarketAggregateResponse;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.service.CryptoService;
import com.emrehalli.financeportal.market.service.FxService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    private final MarketQueryService marketQueryService;

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

    @GetMapping({"/{symbol}", "/symbol/{symbol}"})
    public ApiResponse<MarketQueryService.MarketSnapshot> getMarketBySymbol(@PathVariable String symbol) {
        MarketQueryService.MarketSnapshot data = marketQueryService.findBySymbol(symbol).orElse(null);
        return ApiResponse.<MarketQueryService.MarketSnapshot>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.fetchedAt() : null)
                .build();
    }

    @GetMapping({"/{symbol}/history", "/history/{symbol}"})
    public ApiResponse<List<MarketQueryService.HistoricalPrice>> getMarketHistory(
            @PathVariable String symbol,
            @RequestParam(name = "source", required = false) SourceName sourceName,
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate resolvedTo = firstNonNull(to, endDate, LocalDate.now());
        LocalDate resolvedFrom = resolveFromDate(range, firstNonNull(from, startDate), resolvedTo);

        List<MarketQueryService.HistoricalPrice> data = marketQueryService.getHistory(
                symbol,
                sourceName,
                resolvedFrom,
                resolvedTo
        );

        LocalDateTime dataDate = data.stream()
                .map(MarketQueryService.HistoricalPrice::priceDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(LocalDate::atStartOfDay)
                .orElse(null);

        return ApiResponse.<List<MarketQueryService.HistoricalPrice>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(dataDate)
                .build();
    }

    private boolean isMacroIndicatorRequest(String type) {
        return type != null && "MACRO_INDICATOR".equalsIgnoreCase(type.trim());
    }

    private LocalDate resolveFromDate(String range, LocalDate explicitFrom, LocalDate resolvedTo) {
        if (explicitFrom != null) {
            return explicitFrom;
        }

        if (range == null || range.isBlank()) {
            return resolvedTo.minusDays(90);
        }

        return switch (range.trim().toLowerCase()) {
            case "7d", "1w" -> resolvedTo.minusDays(7);
            case "1m" -> resolvedTo.minusMonths(1);
            case "3m" -> resolvedTo.minusMonths(3);
            case "1y" -> resolvedTo.minusYears(1);
            case "max" -> LocalDate.of(2000, 1, 1);
            default -> resolvedTo.minusDays(90);
        };
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
