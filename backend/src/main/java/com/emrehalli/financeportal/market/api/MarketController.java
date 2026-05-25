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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;

    @GetMapping
    public ApiResponse<MarketAggregateResponse> getAllMarkets(@RequestParam(name = "type", required = false) String type) {
        boolean macroIndicatorRequest = isMacroIndicatorRequest(type);
        List<FxRateResponse> fx = macroIndicatorRequest ? List.of() : fxService.getAll();
        List<MarketQueryService.MarketSnapshot> cryptoSnapshots = macroIndicatorRequest ? List.of() : cryptoService.getAll();
        List<StockPriceDto> stockQuotes = macroIndicatorRequest
                ? List.of()
                : stockService.getAll(PageRequest.of(0, 500)).getContent();
        List<FundNavDto> fundQuotes = macroIndicatorRequest ? List.of() : fundService.getAll();
        List<Object> crypto = new ArrayList<>(cryptoSnapshots);
        List<Object> stocks = new ArrayList<>(stockQuotes.stream()
                .map(this::toMarketSnapshot)
                .toList());
        List<Object> funds = new ArrayList<>(fundQuotes.stream()
                .map(fund -> toMarketSnapshot(fund, SourceName.TEFAS.name()))
                .toList());
        MarketAggregateResponse data = MarketAggregateResponse.builder()
                .fx(fx)
                .crypto(crypto)
                .stocks(stocks)
                .funds(funds)
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

                    if (instrument.getInstrumentType() == InstrumentType.STOCK) {
                        return stockService.getHistory(
                                        symbol.toUpperCase(),
                                        resolvedFrom.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                                        resolvedTo.atZone(java.time.ZoneOffset.UTC).toLocalDate()
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
            case STOCK -> SourceName.BIST;
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
        BigDecimal changeRate = null;
        if (fund.getPreviousNavValue() != null
                && fund.getPreviousNavValue().compareTo(BigDecimal.ZERO) != 0) {
            changeRate = fund.getNavValue()
                    .subtract(fund.getPreviousNavValue())
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
                LocalDateTime.now()
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
                || normalized.contains("türk")
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
                .replace('ı', 'i')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ç', 'c')
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

    private record FxSearchLabel(String source, String currencyCode, String currencyName) {
    }

    private record InstrumentSearchResult(String code, String name, String type) {
    }

    private record PriceOnDateResult(String symbol, LocalDate date, BigDecimal price) {
    }
}



