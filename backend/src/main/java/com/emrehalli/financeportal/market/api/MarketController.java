package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.api.dto.MarketAggregateResponse;
import com.emrehalli.financeportal.market.api.dto.PriceHistoryDto;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.service.CryptoService;
import com.emrehalli.financeportal.market.service.FundService;
import com.emrehalli.financeportal.market.service.FxService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.market.service.StockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for aggregate market data.
 */
@RestController
@RequestMapping("/api/v1/markets")
@AllArgsConstructor
@Slf4j
public class MarketController {

    private final FxService fxService;
    private final CryptoService cryptoService;
    private final FundService fundService;
    private final StockService stockService;
    private final MarketQueryService marketQueryService;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;

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
        String normalizedSymbol = symbol.toUpperCase();
        MarketQueryService.MarketSnapshot data = marketQueryService.findBySymbol(normalizedSymbol)
                .map(snapshot -> resolveMarketSnapshot(normalizedSymbol, snapshot))
                .or(() -> {
                    try {
                        StockPriceDto stock = stockService.getBySymbol(normalizedSymbol);
                        return stock != null ? java.util.Optional.of(toMarketSnapshot(stock)) : java.util.Optional.empty();
                    } catch (Exception exception) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
        return ApiResponse.<MarketQueryService.MarketSnapshot>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.fetchedAt() : null)
                .build();
    }

    @GetMapping("/{symbol}/history")
    public ApiResponse<List<PriceHistoryDto>> getMarketHistory(
            @PathVariable String symbol,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "source", required = false) String source
    ) {
        Instant resolvedTo = resolveTo(to);
        Instant resolvedFrom = resolveFrom(from, to, period, resolvedTo);
        SourceName sourceName = resolveSource(source);

        List<PriceHistoryDto> data = instrumentRepository.findByInstrumentCodeIgnoreCase(symbol.toUpperCase())
                .map(instrument -> {
                    if (instrument.getInstrumentType() == InstrumentType.CRYPTO) {
                        return historyRepository
                                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                                        instrument,
                                        IntervalType.ONE_DAY,
                                        sourceName,
                                        resolvedFrom,
                                        resolvedTo
                                ).stream()
                                .map(this::toDto)
                                .toList();
                    }

                    if (instrument.getInstrumentType() == InstrumentType.STOCK) {
                        return stockService.getHistory(
                                        symbol.toUpperCase(),
                                        resolvedFrom.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                                        resolvedTo.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                ).stream()
                                .map(this::toDto)
                                .toList();
                    }

                    return List.<PriceHistoryDto>of();
                })
                .orElse(List.of());

        return ApiResponse.<List<PriceHistoryDto>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    private boolean isMacroIndicatorRequest(String type) {
        return type != null && "MACRO_INDICATOR".equalsIgnoreCase(type.trim());
    }

    private MarketQueryService.MarketSnapshot resolveMarketSnapshot(String symbol, MarketQueryService.MarketSnapshot fallbackSnapshot) {
        InstrumentType instrumentType = parseInstrumentType(fallbackSnapshot.instrumentType());
        SourceName sourceName = parseSourceName(fallbackSnapshot.source());

        if (instrumentType == InstrumentType.CRYPTO && sourceName == SourceName.BINANCE) {
            return cryptoService.getBySymbol(symbol);
        }

        if (instrumentType == InstrumentType.FX) {
            String currencyCode = symbol.contains(":")
                    ? symbol.split(":")[1]
                    : symbol;

            return fxService.getByCode(currencyCode).stream()
                    .findFirst()
                    .map(this::toMarketSnapshot)
                    .orElse(fallbackSnapshot);
        }

        if (instrumentType == InstrumentType.FUND) {
            FundNavDto fund = fundService.getByCode(symbol);
            return fund != null ? toMarketSnapshot(fund, fallbackSnapshot.source()) : fallbackSnapshot;
        }

        if (instrumentType == InstrumentType.STOCK) {
            StockPriceDto stock = stockService.getBySymbol(symbol);
            return stock != null ? toMarketSnapshot(stock) : fallbackSnapshot;
        }

        return fallbackSnapshot;
    }

    private Instant resolveFrom(String from, String to, String period, Instant resolvedTo) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        }

        if (to != null && !to.isBlank()) {
            return resolvedTo.minus(90, ChronoUnit.DAYS);
        }

        if (period == null || period.isBlank()) {
            return resolvedTo.minus(90, ChronoUnit.DAYS);
        }

        return switch (period.trim().toUpperCase()) {
            case "1D" -> resolvedTo.minus(1, ChronoUnit.DAYS);
            case "1W" -> resolvedTo.minus(7, ChronoUnit.DAYS);
            case "1M" -> resolvedTo.minus(30, ChronoUnit.DAYS);
            case "3M" -> resolvedTo.minus(90, ChronoUnit.DAYS);
            case "6M" -> resolvedTo.minus(180, ChronoUnit.DAYS);
            case "1Y" -> resolvedTo.minus(365, ChronoUnit.DAYS);
            case "MAX" -> resolvedTo.minus(3650, ChronoUnit.DAYS);
            default -> resolvedTo.minus(90, ChronoUnit.DAYS);
        };
    }

    private Instant resolveTo(String to) {
        if (to == null || to.isBlank()) {
            return Instant.now();
        }
        return LocalDate.parse(to)
                .plusDays(1)
                .atStartOfDay()
                .minusNanos(1)
                .toInstant(java.time.ZoneOffset.UTC);
    }

    private SourceName resolveSource(String source) {
        if (source == null || source.isBlank()) {
            return SourceName.BINANCE;
        }

        try {
            return SourceName.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return SourceName.BINANCE;
        }
    }

    private PriceHistoryDto toDto(MarketPriceHistory history) {
        return PriceHistoryDto.builder()
                .priceTimestamp(history.getPriceTimestamp())
                .openPrice(history.getOpenPrice())
                .highPrice(history.getHighPrice())
                .lowPrice(history.getLowPrice())
                .closePrice(history.getClosePrice())
                .volume(history.getVolume())
                .build();
    }

    private PriceHistoryDto toDto(StockHistoryDto history) {
        return PriceHistoryDto.builder()
                .priceTimestamp(history.priceTimestamp())
                .openPrice(history.openPrice())
                .highPrice(history.highPrice())
                .lowPrice(history.lowPrice())
                .closePrice(history.closePrice())
                .volume(history.volume())
                .build();
    }

    private MarketQueryService.MarketSnapshot toMarketSnapshot(FxRateResponse rate) {
        log.debug("[MarketController] FX dispatch changePercent={}", rate.getChangePercent());
        return new MarketQueryService.MarketSnapshot(
                rate.getCode(),
                rate.getName(),
                rate.getLast(),
                rate.getChangePercent(),
                rate.getSource(),
                rate.getType(),
                "TRY",
                rate.getPriceTimestamp()
        );
    }

    private MarketQueryService.MarketSnapshot toMarketSnapshot(FundNavDto fund, String source) {
        return new MarketQueryService.MarketSnapshot(
                fund.getFundCode(),
                fund.getFundName(),
                fund.getNavValue(),
                null,
                source,
                InstrumentType.FUND.name(),
                "TRY",
                fund.getNavDate() != null ? fund.getNavDate().atStartOfDay() : null
        );
    }

    private MarketQueryService.MarketSnapshot toMarketSnapshot(StockPriceDto stock) {
        return new MarketQueryService.MarketSnapshot(
                stock.symbol(),
                stock.symbol(),
                stock.price(),
                stock.changePercent(),
                stock.sourceName(),
                InstrumentType.STOCK.name(),
                null,
                stock.dataTimestamp() != null
                        ? LocalDateTime.ofInstant(stock.dataTimestamp(), java.time.ZoneOffset.UTC)
                        : null
        );
    }

    private InstrumentType parseInstrumentType(String instrumentType) {
        try {
            return instrumentType != null ? InstrumentType.valueOf(instrumentType) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SourceName parseSourceName(String source) {
        try {
            return source != null ? SourceName.valueOf(source) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
