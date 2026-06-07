package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.api.dto.FxRateResponse;
import com.emrehalli.financeportal.market.api.dto.MarketAggregateResponse;
import com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse;
import com.emrehalli.financeportal.market.api.dto.MarketScreenResponse;
import com.emrehalli.financeportal.market.api.dto.PriceHistoryDto;
import com.emrehalli.financeportal.market.cache.CacheService;
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
import com.emrehalli.financeportal.market.service.MarketScreenCriteria;
import com.emrehalli.financeportal.market.service.MarketScreeningService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.market.service.StockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.context.ContextSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * REST controller for aggregate market data.
 */
@RestController
@RequestMapping("/api/v1/markets")
@AllArgsConstructor
@Slf4j
public class MarketController {

    private static final String INTERNAL_CASH_CODE = "TRY";
    private static final String INTERNAL_CASH_DISPLAY_NAME = "Nakit";

    private final FxService fxService;
    private final CryptoService cryptoService;
    private final FundService fundService;
    private final StockService stockService;
    private final MarketQueryService marketQueryService;
    private final MarketScreeningService marketScreeningService;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;
    private final CacheService cacheService;
    private final MarketProperties marketProperties;

    @GetMapping
    public ApiResponse<MarketAggregateResponse> getAllMarkets(@RequestParam(name = "type", required = false) String type) {
        boolean macroIndicatorRequest = isMacroIndicatorRequest(type);
        String cacheKey = "markets:aggregate:" + (macroIndicatorRequest ? "macro" : "default");
        MarketAggregateResponse cached = getCachedAggregate(cacheKey);
        if (cached != null) {
            return ApiResponse.<MarketAggregateResponse>builder()
                    .success(true)
                    .message("OK")
                    .data(cached)
                    .build();
        }

        List<FxRateResponse> fx;
        List<MarketScreenItemResponse> cryptoRows;
        List<MarketScreenItemResponse> stockRows;
        List<MarketScreenItemResponse> fundRows;

        if (macroIndicatorRequest) {
            fx = List.of();
            cryptoRows = List.of();
            stockRows = List.of();
            fundRows = List.of();
        } else {
            ContextSnapshot contextSnapshot = ContextSnapshot.captureAll();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var fxFuture = CompletableFuture.supplyAsync(withContext(contextSnapshot, fxService::getAll), executor)
                        .exceptionally(ex -> { log.warn("FX load failed on aggregate", ex); return List.of(); });
                var cryptoFuture = CompletableFuture.supplyAsync(withContext(contextSnapshot, () -> screenCategory("CRYPTO", 100)), executor)
                        .exceptionally(ex -> { log.warn("Crypto screen failed on aggregate", ex); return List.of(); });
                var stockFuture = CompletableFuture.supplyAsync(withContext(contextSnapshot, () -> screenCategory("STOCK", 500)), executor)
                        .exceptionally(ex -> { log.warn("Stock screen failed on aggregate", ex); return List.of(); });
                var fundFuture = CompletableFuture.supplyAsync(withContext(contextSnapshot, () -> screenCategory("FUND", 500)), executor)
                        .exceptionally(ex -> { log.warn("Fund screen failed on aggregate", ex); return List.of(); });

                CompletableFuture.allOf(fxFuture, cryptoFuture, stockFuture, fundFuture).join();
                fx = fxFuture.join();
                cryptoRows = cryptoFuture.join();
                stockRows = stockFuture.join();
                fundRows = fundFuture.join();
            }
        }
        List<Object> crypto = new ArrayList<>(cryptoRows.stream().map(this::toMarketSnapshot).toList());
        List<Object> stocks = new ArrayList<>(stockRows.stream().map(this::toMarketSnapshot).toList());
        List<Object> funds = new ArrayList<>(fundRows.stream().map(this::toMarketSnapshot).toList());
        MarketAggregateResponse data = MarketAggregateResponse.builder()
                .fx(fx)
                .crypto(crypto)
                .stocks(stocks)
                .funds(funds)
                .futures(List.of())
                .bonds(List.of())
                .build();

        putCachedAggregate(cacheKey, data);

        return ApiResponse.<MarketAggregateResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(fx, cryptoRows))
                .build();
    }

    private MarketAggregateResponse getCachedAggregate(String cacheKey) {
        try {
            return cacheService.get(cacheKey, MarketAggregateResponse.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read market aggregate cache key={}", cacheKey, exception);
            return null;
        }
    }

    private void putCachedAggregate(String cacheKey, MarketAggregateResponse data) {
        try {
            cacheService.put(cacheKey, data, Math.max(1, Math.min(2, marketProperties.getCache().getTtlMinutes().getStock())));
        } catch (Exception exception) {
            log.warn("Failed to write market aggregate cache key={}", cacheKey, exception);
        }
    }

    private List<MarketScreenItemResponse> screenCategory(String type, int size) {
        return marketScreeningService.screen(MarketScreenCriteria.builder()
                .type(type)
                .page(0)
                .size(size)
                .sort("symbol")
                .build()).getContent();
    }

    @GetMapping("/screen")
    public ApiResponse<MarketScreenResponse> screenMarkets(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "symbols", required = false) List<String> symbols,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "minChangePercent", required = false) BigDecimal minChangePercent,
            @RequestParam(name = "maxChangePercent", required = false) BigDecimal maxChangePercent,
            @RequestParam(name = "sector", required = false) String sector,
            @RequestParam(name = "market", required = false) String market,
            @RequestParam(name = "minPe", required = false) BigDecimal minPe,
            @RequestParam(name = "maxPe", required = false) BigDecimal maxPe,
            @RequestParam(name = "minPb", required = false) BigDecimal minPb,
            @RequestParam(name = "maxPb", required = false) BigDecimal maxPb,
            @RequestParam(name = "minRoe", required = false) BigDecimal minRoe,
            @RequestParam(name = "minRoa", required = false) BigDecimal minRoa,
            @RequestParam(name = "maxDebtToEquity", required = false) BigDecimal maxDebtToEquity,
            @RequestParam(name = "minRevenueGrowth", required = false) BigDecimal minRevenueGrowth,
            @RequestParam(name = "minNetProfitGrowth", required = false) BigDecimal minNetProfitGrowth,
            @RequestParam(name = "onlyWithFundamentals", defaultValue = "false") boolean onlyWithFundamentals,
            @RequestParam(name = "bistTier", required = false) String bistTier,
            @RequestParam(name = "stockSector", required = false) String stockSector,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        if (isFxScreenRequest(type)) {
            MarketScreenResponse data = screenFxMarkets(type, source, q, minPrice, maxPrice, minChangePercent, maxChangePercent, page, size, sort);
            return ApiResponse.<MarketScreenResponse>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .build();
        }

        validateRange("price", minPrice, maxPrice);
        validateRange("changePercent", minChangePercent, maxChangePercent);
        validateRange("peRatio", minPe, maxPe);
        validateRange("pbRatio", minPb, maxPb);

        Page<com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse> result = marketScreeningService.screen(
                MarketScreenCriteria.builder()
                        .type(type)
                        .source(source)
                        .q(q)
                        .symbols(normalizeSymbolFilter(symbols))
                        .minPrice(minPrice)
                        .maxPrice(maxPrice)
                        .minChangePercent(minChangePercent)
                        .maxChangePercent(maxChangePercent)
                        .sector(sector)
                        .market(market)
                        .minPe(minPe)
                        .maxPe(maxPe)
                        .minPb(minPb)
                        .maxPb(maxPb)
                        .minRoe(minRoe)
                        .minRoa(minRoa)
                        .maxDebtToEquity(maxDebtToEquity)
                        .minRevenueGrowth(minRevenueGrowth)
                        .minNetProfitGrowth(minNetProfitGrowth)
                        .onlyWithFundamentals(onlyWithFundamentals)
                        .bistTier(bistTier)
                        .stockSector(stockSector)
                        .page(page)
                        .size(size)
                        .sort(sort)
                        .build()
        );

        MarketScreenResponse data = MarketScreenResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize())
                .build();

        return ApiResponse.<MarketScreenResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    private MarketScreenResponse screenFxMarkets(String type,
                                                 String source,
                                                 String q,
                                                 BigDecimal minPrice,
                                                 BigDecimal maxPrice,
                                                 BigDecimal minChangePercent,
                                                 BigDecimal maxChangePercent,
                                                 int page,
                                                 int size,
                                                 String sort) {
        validateRange("price", minPrice, maxPrice);
        validateRange("changePercent", minChangePercent, maxChangePercent);

        List<com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse> filtered = fxService.getAll().stream()
                .filter(rate -> matchesFxSource(rate, source))
                .filter(rate -> matchesFxQuery(rate, q))
                .filter(rate -> matchesFxPrice(rate, minPrice, maxPrice))
                .filter(rate -> matchesFxChange(rate, minChangePercent, maxChangePercent))
                .sorted((left, right) -> compareFxItems(left, right, sort))
                .map(this::toFxScreenItem)
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        return MarketScreenResponse.builder()
                .content(filtered.subList(fromIndex, toIndex))
                .totalElements(filtered.size())
                .page(safePage)
                .size(safeSize)
                .build();
    }

    @GetMapping("/instruments/search")
    public ApiResponse<List<InstrumentSearchResult>> searchInstruments(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "includeInternal", defaultValue = "false") boolean includeInternal
    ) {
        int clampedLimit = Math.min(Math.max(limit, 1), 100);
        List<InstrumentSearchResult> results;
        if (query == null || query.isBlank()) {
            results = List.of();
        } else {
            Pageable pageable = PageRequest.of(0, clampedLimit);
            List<MarketInstrument> directMatches = new ArrayList<>(instrumentRepository
                    .findByInstrumentCodeContainingIgnoreCaseOrInstrumentNameContainingIgnoreCase(
                            query, query, pageable));
            List<MarketInstrument> aliasMatches = instrumentRepository.findAllWithNonBlankInstrumentCode().stream()
                    .filter(this::isFxSellInstrument)
                    .filter(instrument -> matchesFxAlias(query, instrument))
                    .toList();

            LinkedHashMap<String, InstrumentSearchResult> deduped = new LinkedHashMap<>();
            directMatches.stream()
                    .filter(instrument -> includeInternal || !isInternalCashInstrument(instrument))
                    .filter(this::shouldIncludeInPortfolioSearch)
                    .map(this::toInstrumentSearchResult)
                    .forEach(result -> deduped.putIfAbsent(result.code(), result));

            aliasMatches.stream()
                    .map(this::toInstrumentSearchResult)
                    .forEach(result -> deduped.putIfAbsent(result.code(), result));

            results = new ArrayList<>(deduped.values().stream()
                    .limit(clampedLimit)
                    .toList());

            if (includeInternal
                    && matchesInternalCashAlias(query)
                    && results.stream().noneMatch(item -> INTERNAL_CASH_CODE.equalsIgnoreCase(item.code()))) {
                instrumentRepository.findByInstrumentCodeIgnoreCase(INTERNAL_CASH_CODE)
                        .map(this::toInstrumentSearchResult)
                        .ifPresent(result -> results.add(0, result));
            }
        }
        return ApiResponse.<List<InstrumentSearchResult>>builder()
                .success(true)
                .message("OK")
                .data(results)
                .build();
    }

    @GetMapping("/{symbol}/price-on-date")
    public ApiResponse<PriceOnDateResult> getPriceOnDate(
            @PathVariable String symbol,
            @RequestParam(name = "date") String date
    ) {
        LocalDate targetDate = LocalDate.parse(date);
        String normalizedSymbol = symbol.toUpperCase();
        List<MarketQueryService.HistoricalPrice> history = marketQueryService.getHistory(
                normalizedSymbol, null, targetDate.minusDays(7), targetDate);
        PriceOnDateResult result = history.stream()
                .filter(p -> !p.priceDate().isAfter(targetDate))
                .reduce((first, second) -> second)
                .map(p -> new PriceOnDateResult(normalizedSymbol, p.priceDate(), p.closePrice()))
                .orElse(null);
        return ApiResponse.<PriceOnDateResult>builder()
                .success(true)
                .message("OK")
                .data(result)
                .build();
    }

    @GetMapping({"/{symbol}", "/symbol/{symbol}"})
    public ApiResponse<MarketQueryService.MarketSnapshot> getMarketBySymbol(
            @PathVariable String symbol,
            @RequestParam(name = "type", required = false) InstrumentType type
    ) {
        String normalizedSymbol = symbol.toUpperCase();
        MarketQueryService.MarketSnapshot data = marketQueryService.findBySymbol(normalizedSymbol, type)
                .map(snapshot -> resolveMarketSnapshot(normalizedSymbol, snapshot))
                .or(() -> {
                    if (type != null && type != InstrumentType.STOCK) {
                        return java.util.Optional.empty();
                    }
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
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "type", required = false) InstrumentType type
    ) {
        Instant resolvedTo = resolveTo(to);
        Instant resolvedFrom = resolveFrom(from, to, period, resolvedTo);
        SourceName sourceName = resolveSource(source);

        List<PriceHistoryDto> data = resolveInstrument(symbol.toUpperCase(), type)
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

                    return getStoredHistory(instrument, sourceName, resolvedFrom, resolvedTo).stream()
                            .map(this::toDto)
                            .toList();
                })
                .orElse(List.of());

        return ApiResponse.<List<PriceHistoryDto>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    private List<MarketPriceHistory> getStoredHistory(
            MarketInstrument instrument,
            SourceName sourceName,
            Instant resolvedFrom,
            Instant resolvedTo
    ) {
        if (sourceName == null) {
            return historyRepository.findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                    instrument,
                    IntervalType.ONE_DAY,
                    resolvedFrom,
                    resolvedTo
            );
        }

        List<MarketPriceHistory> sourceHistory = historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                instrument,
                IntervalType.ONE_DAY,
                sourceName,
                resolvedFrom,
                resolvedTo
        );

        if (!sourceHistory.isEmpty()) {
            return sourceHistory;
        }

        return historyRepository.findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                instrument,
                IntervalType.ONE_DAY,
                resolvedFrom,
                resolvedTo
        );
    }

    private java.util.Optional<MarketInstrument> resolveInstrument(String symbol, InstrumentType type) {
        SourceName sourceName = resolveInstrumentSource(symbol, type);
        if (sourceName != null) {
            return instrumentRepository.findByInstrumentCodeIgnoreCaseAndSourceName(symbol, sourceName);
        }

        if (type != null) {
            return instrumentRepository.findFirstByInstrumentCodeIgnoreCaseAndInstrumentTypeOrderByCreatedAtAsc(symbol, type);
        }

        return instrumentRepository.findFirstByInstrumentCodeIgnoreCaseOrderByCreatedAtAsc(symbol);
    }

    private SourceName resolveInstrumentSource(String symbol, InstrumentType type) {
        if (type == null) {
            return null;
        }

        return switch (type) {
            case FUND -> SourceName.TEFAS;
            case CRYPTO -> SourceName.BINANCE;
            case STOCK -> SourceName.YAHOO_FINANCE;
            case FX -> parseSourceFromSymbol(symbol);
            case INDEX -> SourceName.YAHOO_FINANCE;
            case COMMODITY -> null; // mixed sources (YAHOO_FINANCE for BRENT, CALCULATED for gold/silver)
            default -> null;
        };
    }

    private SourceName parseSourceFromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || !symbol.contains(":")) {
            return null;
        }

        try {
            return SourceName.valueOf(symbol.split(":")[0].trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

            return fxService.getBySourceAndCode(sourceName, currencyCode)
                    .or(() -> fxService.getByCode(currencyCode).stream().findFirst())
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
                rate.getPriceTimestamp(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private MarketQueryService.MarketSnapshot toMarketSnapshot(FundNavDto fund, String source) {
        BigDecimal changeRate = null;
        if (fund.getPreviousNavValue() != null
                && fund.getPreviousNavValue().compareTo(BigDecimal.ZERO) != 0) {
            changeRate = fund.getNavValue()
                    .subtract(fund.getPreviousNavValue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(fund.getPreviousNavValue(), 4, RoundingMode.HALF_UP);
        }

        return new MarketQueryService.MarketSnapshot(
                fund.getFundCode(),
                fund.getFundName(),
                fund.getNavValue(),
                changeRate,
                source,
                InstrumentType.FUND.name(),
                "TRY",
                LocalDateTime.now(),
                null,
                null,
                fund.getPreviousNavValue(),
                null,
                null
        );
    }

    private MarketQueryService.MarketSnapshot toMarketSnapshot(MarketScreenItemResponse item) {
        return new MarketQueryService.MarketSnapshot(
                item.getSymbol(),
                item.getName(),
                item.getLastPrice(),
                item.getChangePercent(),
                item.getSource(),
                item.getType(),
                item.getCurrency(),
                item.getDataTimestamp(),
                null,
                item.getVolume() != null ? item.getVolume().longValue() : null,
                null,
                null,
                null
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
                        : null,
                stock.openPrice(),
                stock.volume(),
                stock.previousClose(),
                stock.dayHigh(),
                stock.dayLow()
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
                                          List<MarketScreenItemResponse> marketRows) {
        LocalDateTime fxLatest = responses.stream()
                .map(FxRateResponse::getPriceTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime marketLatest = marketRows.stream()
                .map(MarketScreenItemResponse::getDataTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (fxLatest == null) {
            return marketLatest;
        }
        if (marketLatest == null) {
            return fxLatest;
        }
        return fxLatest.isAfter(marketLatest) ? fxLatest : marketLatest;
    }

    private boolean isFxScreenRequest(String type) {
        return type != null && "FX".equalsIgnoreCase(type.trim());
    }

    private boolean matchesFxSource(FxRateResponse rate, String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        return source.trim().equalsIgnoreCase(rate.getSource());
    }

    private boolean matchesFxQuery(FxRateResponse rate, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String normalized = q.trim().toUpperCase(Locale.ROOT);
        return (rate.getCode() != null && rate.getCode().toUpperCase(Locale.ROOT).contains(normalized))
                || (rate.getName() != null && rate.getName().toUpperCase(Locale.ROOT).contains(normalized));
    }

    private boolean matchesFxPrice(FxRateResponse rate, BigDecimal minPrice, BigDecimal maxPrice) {
        BigDecimal comparable = firstNonNull(rate.getSellRate(), rate.getLast(), rate.getBuyRate());
        if (minPrice != null && (comparable == null || comparable.compareTo(minPrice) < 0)) {
            return false;
        }
        if (maxPrice != null && (comparable == null || comparable.compareTo(maxPrice) > 0)) {
            return false;
        }
        return true;
    }

    private boolean matchesFxChange(FxRateResponse rate, BigDecimal minChangePercent, BigDecimal maxChangePercent) {
        BigDecimal comparable = rate.getChangePercent();
        if (minChangePercent != null && (comparable == null || comparable.compareTo(minChangePercent) < 0)) {
            return false;
        }
        if (maxChangePercent != null && (comparable == null || comparable.compareTo(maxChangePercent) > 0)) {
            return false;
        }
        return true;
    }

    private int compareFxItems(FxRateResponse left, FxRateResponse right, String sort) {
        String normalized = sort != null ? sort.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "price_asc" -> compareNullable(firstNonNull(left.getSellRate(), left.getLast(), left.getBuyRate()),
                    firstNonNull(right.getSellRate(), right.getLast(), right.getBuyRate()), true, left.getCode(), right.getCode());
            case "price_desc" -> compareNullable(firstNonNull(left.getSellRate(), left.getLast(), left.getBuyRate()),
                    firstNonNull(right.getSellRate(), right.getLast(), right.getBuyRate()), false, left.getCode(), right.getCode());
            case "change_asc" -> compareNullable(left.getChangePercent(), right.getChangePercent(), true, left.getCode(), right.getCode());
            case "change_desc" -> compareNullable(left.getChangePercent(), right.getChangePercent(), false, left.getCode(), right.getCode());
            default -> compareText(left.getCode(), right.getCode());
        };
    }

    private int compareNullable(BigDecimal left, BigDecimal right, boolean ascending, String leftCode, String rightCode) {
        if (left == null && right == null) {
            return compareText(leftCode, rightCode);
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int compared = left.compareTo(right);
        if (!ascending) {
            compared = compared * -1;
        }
        return compared != 0 ? compared : compareText(leftCode, rightCode);
    }

    private int compareText(String left, String right) {
        String safeLeft = left != null ? left : "";
        String safeRight = right != null ? right : "";
        return safeLeft.compareToIgnoreCase(safeRight);
    }

    private List<String> normalizeSymbolFilter(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        return symbols.stream()
                .filter(item -> item != null && !item.isBlank())
                .flatMap(item -> Arrays.stream(item.split(",")))
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(100)
                .toList();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse toFxScreenItem(FxRateResponse rate) {
        String code = rate.getCode() != null ? rate.getCode().toUpperCase(Locale.ROOT) : null;
        String name = rate.getName();
        String displayName = (name == null || name.isBlank() || code != null && name.equalsIgnoreCase(code))
                ? (code != null ? code + "/TRY" : null)
                : name;

        return com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse.builder()
                .symbol(code)
                .name(displayName)
                .type(InstrumentType.FX.name())
                .displayUnit("CURRENCY")
                .currency("TRY")
                .source(rate.getSource())
                .buyPrice(rate.getBuyRate())
                .sellPrice(rate.getSellRate())
                .buyChangePercent(null)
                .sellChangePercent(rate.getChangePercent())
                .lastPrice(rate.getLast())
                .changePercent(rate.getChangePercent())
                .dataTimestamp(rate.getPriceTimestamp())
                .build();
    }

    private void validateRange(String fieldName, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException(fieldName + " min deÃ„Å¸eri max deÃ„Å¸erinden bÃƒÂ¼yÃƒÂ¼k olamaz.");
        }
    }

    private InstrumentSearchResult toInstrumentSearchResult(MarketInstrument instrument) {
        String displayName = isInternalCashInstrument(instrument)
                ? INTERNAL_CASH_DISPLAY_NAME
                : resolveInstrumentSearchDisplayName(instrument);
        return new InstrumentSearchResult(
                instrument.getInstrumentCode(),
                displayName,
                instrument.getInstrumentType().name());
    }

    private String resolveInstrumentSearchDisplayName(MarketInstrument instrument) {
        if (instrument.getInstrumentType() == InstrumentType.FX) {
            FxSearchLabel label = extractFxSearchLabel(instrument.getInstrumentCode());
            if (label != null) {
                return label.currencyCode() + " / " + label.currencyName();
            }
        }
        return instrument.getInstrumentName();
    }

    private boolean matchesInternalCashAlias(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("nakit")
                || normalized.contains("cash")
                || normalized.contains("try")
                || normalized.contains("turk")
                || normalized.contains("tÃƒÂ¼rk")
                || normalized.contains("lira");
    }

    private boolean isInternalCashInstrument(MarketInstrument instrument) {
        return instrument.getInstrumentType() == InstrumentType.FX
                && instrument.getSourceName() == SourceName.INTERNAL
                && INTERNAL_CASH_CODE.equalsIgnoreCase(instrument.getInstrumentCode());
    }

    private boolean shouldIncludeInPortfolioSearch(MarketInstrument instrument) {
        if (instrument.getInstrumentType() != InstrumentType.FX) {
            return true;
        }
        return isInternalCashInstrument(instrument) || isFxSellInstrument(instrument);
    }

    private boolean isFxSellInstrument(MarketInstrument instrument) {
        if (instrument == null || instrument.getInstrumentType() != InstrumentType.FX) {
            return false;
        }
        String code = instrument.getInstrumentCode();
        return code != null && code.trim().toUpperCase(Locale.ROOT).endsWith(":SELL");
    }

    private boolean matchesFxAlias(String query, MarketInstrument instrument) {
        if (query == null || query.isBlank()) {
            return false;
        }
        FxSearchLabel label = extractFxSearchLabel(instrument.getInstrumentCode());
        if (label == null) {
            return false;
        }

        String normalizedQuery = normalizeSearchText(query);
        return normalizeSearchText(label.currencyCode()).contains(normalizedQuery)
                || normalizeSearchText(label.currencyName()).contains(normalizedQuery)
                || normalizeSearchText(label.currencyCode() + " " + label.currencyName()).contains(normalizedQuery);
    }

    private FxSearchLabel extractFxSearchLabel(String instrumentCode) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return null;
        }

        String[] parts = instrumentCode.trim().toUpperCase(Locale.ROOT).split(":");
        if (parts.length != 3 || !"SELL".equals(parts[2])) {
            return null;
        }

        String currencyCode = parts[1];
        String currencyName = FX_SEARCH_ALIASES.getOrDefault(currencyCode, currencyCode);
        return new FxSearchLabel(parts[0], currencyCode, currencyName);
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('\u0131', 'i')
                .replace('\u011f', 'g')
                .replace('\u00fc', 'u')
                .replace('\u015f', 's')
                .replace('\u00f6', 'o')
                .replace('\u00e7', 'c')
                .trim();
    }

    private static final Map<String, String> FX_SEARCH_ALIASES = Map.ofEntries(
            Map.entry("USD", "Dolar"),
            Map.entry("EUR", "Euro"),
            Map.entry("GBP", "Sterlin"),
            Map.entry("AUD", "Avustralya Dolari"),
            Map.entry("CAD", "Kanada Dolari"),
            Map.entry("CHF", "Frank"),
            Map.entry("JPY", "Yen"),
            Map.entry("SAR", "Riyal"),
            Map.entry("RUB", "Ruble"),
            Map.entry("AZN", "Manat"),
            Map.entry("CNY", "Yuan"),
            Map.entry("QAR", "Riyal"),
            Map.entry("KWD", "Dinar"),
            Map.entry("KRW", "Won")
    );

    private <T> Supplier<T> withContext(ContextSnapshot snapshot, Supplier<T> supplier) {
        return () -> {
            try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                return supplier.get();
            }
        };
    }

    private record FxSearchLabel(String source, String currencyCode, String currencyName) {
    }

    private record InstrumentSearchResult(String code, String name, String type) {
    }

    private record PriceOnDateResult(String symbol, LocalDate date, BigDecimal price) {
    }
}




