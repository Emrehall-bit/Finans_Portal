package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.support.BinancePairMapper;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalCandleDto;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisValidationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

@Service
public class TechnicalCandleService {

    private static final String SUPPORTED_INTERVAL = "1d";
    private static final Logger logger = LogManager.getLogger(TechnicalCandleService.class);

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final MovingAverageService movingAverageService;
    private final RsiService rsiService;
    private final BinancePairMapper binancePairMapper;

    public TechnicalCandleService(MarketInstrumentRepository marketInstrumentRepository,
                                  MarketPriceHistoryRepository marketPriceHistoryRepository,
                                  MovingAverageService movingAverageService,
                                  RsiService rsiService,
                                  BinancePairMapper binancePairMapper) {
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
        this.movingAverageService = movingAverageService;
        this.rsiService = rsiService;
        this.binancePairMapper = binancePairMapper;
    }

    public List<TechnicalCandleDto> getCandles(String symbol, String range, String interval) {
        validateSymbol(symbol);
        validateInterval(interval);

        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = resolveInstrument(normalizedSymbol);

        if (instrument.getInstrumentType() != InstrumentType.CRYPTO || instrument.getSourceName() != SourceName.BINANCE) {
            throw new TechnicalAnalysisValidationException("Candlestick data not available for symbol " + normalizedSymbol);
        }

        String normalizedRange = normalizeRange(range);
        Instant from = resolveStartTimestamp(instrument, normalizedRange);
        Instant to = Instant.now();

        List<MarketPriceHistory> rawHistory = marketPriceHistoryRepository
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument,
                        IntervalType.ONE_DAY,
                        SourceName.BINANCE,
                        from,
                        to
                );

        List<MarketPriceHistory> history = sanitizeHistory(rawHistory);

        logResolvedHistory(normalizedSymbol, normalizedRange, instrument, from, to, rawHistory, history);

        if (history.isEmpty()) {
            throw new TechnicalAnalysisNotFoundException("Candlestick data not available for symbol " + normalizedSymbol);
        }

        List<BigDecimal> closes = history.stream()
                .map(MarketPriceHistory::getClosePrice)
                .toList();

        List<BigDecimal> sma7 = movingAverageService.calculateSimpleMovingAverage(closes, 7);
        List<BigDecimal> sma20 = movingAverageService.calculateSimpleMovingAverage(closes, 20);
        List<BigDecimal> sma50 = movingAverageService.calculateSimpleMovingAverage(closes, 50);
        List<BigDecimal> rsi14 = rsiService.calculateRsi(closes, 14);

        return IntStream.range(0, history.size())
                .mapToObj(index -> {
                    MarketPriceHistory point = history.get(index);
                    return new TechnicalCandleDto(
                            point.getPriceTimestamp().getEpochSecond(),
                            point.getOpenPrice(),
                            point.getHighPrice(),
                            point.getLowPrice(),
                            point.getClosePrice(),
                            point.getVolume(),
                            valueAt(sma7, index),
                            valueAt(sma20, index),
                            valueAt(sma50, index),
                            valueAt(rsi14, index)
                    );
                })
                .toList();
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new TechnicalAnalysisValidationException("symbol cannot be blank");
        }
    }

    private MarketInstrument resolveInstrument(String symbol) {
        List<String> candidates = buildInstrumentCandidates(symbol);
        logger.info("Candles symbol resolve requested: input={}, candidates={}", symbol, candidates);

        return marketInstrumentRepository.findAllByInstrumentCodeInAndSourceName(candidates, SourceName.BINANCE).stream()
                .filter(instrument -> instrument.getInstrumentType() == InstrumentType.CRYPTO)
                .findFirst()
                .or(() -> candidates.stream()
                        .map(marketInstrumentRepository::findByInstrumentCodeIgnoreCase)
                        .flatMap(java.util.Optional::stream)
                        .findFirst()
                )
                .orElseThrow(() -> new TechnicalAnalysisNotFoundException(
                        "Candlestick data not available for symbol " + symbol
                ));
    }

    private List<String> buildInstrumentCandidates(String symbol) {
        Set<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeSymbol(symbol);
        String displayCode = normalizeSymbol(binancePairMapper.toDisplayCode(normalized));

        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }
        if (!displayCode.isBlank()) {
            candidates.add(displayCode);
        }
        if (!normalized.endsWith("TRY")) {
            candidates.add(normalized + "TRY");
        }
        if (!displayCode.isBlank() && !displayCode.endsWith("TRY")) {
            candidates.add(displayCode + "TRY");
        }

        return List.copyOf(candidates);
    }

    private void validateInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            throw new TechnicalAnalysisValidationException("interval parameter is required");
        }

        if (!SUPPORTED_INTERVAL.equalsIgnoreCase(interval.trim())) {
            throw new TechnicalAnalysisValidationException("Only interval=1d is supported");
        }
    }

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "6m";
        }

        String normalized = range.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1m", "3m", "6m", "1y", "max" -> normalized;
            default -> throw new TechnicalAnalysisValidationException("Unsupported range: " + range);
        };
    }

    private Instant resolveStartTimestamp(MarketInstrument instrument, String range) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        return switch (range) {
            case "1m" -> today.minusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "3m" -> today.minusMonths(3).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "6m" -> today.minusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "1y" -> today.minusYears(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "max" -> marketPriceHistoryRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampAsc(
                            instrument,
                            IntervalType.ONE_DAY,
                            SourceName.BINANCE
                    )
                    .map(MarketPriceHistory::getPriceTimestamp)
                    .orElse(now);
            default -> now;
        };
    }

    private boolean hasCompleteOhlc(MarketPriceHistory point) {
        return point != null
                && point.getOpenPrice() != null
                && point.getHighPrice() != null
                && point.getLowPrice() != null
                && point.getClosePrice() != null
                && point.getPriceTimestamp() != null;
    }

    private BigDecimal valueAt(List<BigDecimal> series, int index) {
        if (series == null || index < 0 || index >= series.size()) {
            return null;
        }

        return series.get(index);
    }

    private List<MarketPriceHistory> sanitizeHistory(List<MarketPriceHistory> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        Map<Instant, MarketPriceHistory> dedupedByTimestamp = new TreeMap<>();
        for (MarketPriceHistory point : history) {
            if (!hasCompleteOhlc(point)) {
                continue;
            }
            dedupedByTimestamp.put(point.getPriceTimestamp(), point);
        }

        return dedupedByTimestamp.values().stream()
                .sorted(Comparator.comparing(MarketPriceHistory::getPriceTimestamp))
                .toList();
    }

    private void logResolvedHistory(String symbol,
                                    String range,
                                    MarketInstrument instrument,
                                    Instant from,
                                    Instant to,
                                    List<MarketPriceHistory> rawHistory,
                                    List<MarketPriceHistory> sanitizedHistory) {
        Instant minTimestamp = sanitizedHistory.isEmpty() ? null : sanitizedHistory.getFirst().getPriceTimestamp();
        Instant maxTimestamp = sanitizedHistory.isEmpty() ? null : sanitizedHistory.getLast().getPriceTimestamp();

        logger.info(
                "Candles history resolved: symbol={}, range={}, instrumentId={}, instrumentCode={}, source={}, rawCount={}, sanitizedCount={}, from={}, to={}, minTimestamp={}, maxTimestamp={}",
                symbol,
                range,
                instrument.getId(),
                instrument.getInstrumentCode(),
                instrument.getSourceName(),
                rawHistory != null ? rawHistory.size() : 0,
                sanitizedHistory.size(),
                from,
                to,
                minTimestamp,
                maxTimestamp
        );
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }

        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}

