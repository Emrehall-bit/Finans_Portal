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
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    /** En uzun gösterge periyodu (SMA50). Warm-up miktarını bu belirler. */
    private static final int MAX_INDICATOR_PERIOD = 50;
    /**
     * Gösterge warm-up penceresi (gün). Günlük (1d) kripto serisi kesintisiz olduğundan
     * bar sayısı ~ gün sayısıdır; olası boşluklara karşı pay bırakılır.
     */
    private static final long WARMUP_DAYS = MAX_INDICATOR_PERIOD + 15L;
    private static final Logger logger = LogManager.getLogger(TechnicalCandleService.class);

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final IndicatorSeriesCalculator indicatorSeriesCalculator;
    private final BinancePairMapper binancePairMapper;
    private final Clock clock;

    public TechnicalCandleService(MarketInstrumentRepository marketInstrumentRepository,
                                  MarketPriceHistoryRepository marketPriceHistoryRepository,
                                  IndicatorSeriesCalculator indicatorSeriesCalculator,
                                  BinancePairMapper binancePairMapper,
                                  Clock clock) {
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
        this.indicatorSeriesCalculator = indicatorSeriesCalculator;
        this.binancePairMapper = binancePairMapper;
        this.clock = clock;
    }

    @Cacheable(value = "technicalAnalysis", key = "T(com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisInputs).candleCacheKey(#symbol, #range, #interval)")
    public List<TechnicalCandleDto> getCandles(String symbol, String range, String interval) {
        // Candle servisi geçmiş davranışını korur: uzunluk/karakter kontrolü trim edilmiş sembol
        // üzerinde yapılır (blank/null kontrolü için trim önemsizdir, ortak metot zaten ele alır).
        TechnicalAnalysisInputs.validateSymbol(symbol == null ? null : symbol.trim());
        validateInterval(interval);

        String normalizedSymbol = normalizeSymbol(symbol);
        MarketInstrument instrument = resolveInstrument(normalizedSymbol);

        SourceName candleSource = resolveCandleSource(instrument, normalizedSymbol);
        if (candleSource == null) {
            throw new TechnicalAnalysisException.Validation("Candlestick data not available for symbol " + normalizedSymbol);
        }

        String normalizedRange = normalizeRange(range);
        Instant displayStart = resolveStartTimestamp(instrument, candleSource, normalizedRange);
        Instant fetchStart = resolveWarmupStart(normalizedRange, displayStart);
        Instant to = Instant.now(clock);

        List<MarketPriceHistory> rawHistory = marketPriceHistoryRepository
                .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrument,
                        IntervalType.ONE_DAY,
                        candleSource,
                        fetchStart,
                        to
                );

        List<MarketPriceHistory> history = sanitizeHistory(rawHistory);

        logResolvedHistory(normalizedSymbol, normalizedRange, instrument, fetchStart, to, rawHistory, history);

        if (history.isEmpty()) {
            throw new TechnicalAnalysisException.NotFound("Candlestick data not available for symbol " + normalizedSymbol);
        }

        List<BigDecimal> closes = history.stream()
                .map(MarketPriceHistory::getClosePrice)
                .toList();

        // Göstergeler warm-up barları dahil tüm seri üzerinde hesaplanır; böylece seçilen aralığın
        // ilk günlerinde de MA50/RSI gibi uzun periyotlu göstergeler dolu gelir. Candle endpoint'i
        // seçim parametresi almadığından tüm göstergeler hesaplanır.
        Map<IndicatorType, List<BigDecimal>> series = indicatorSeriesCalculator.calculate(closes, IndicatorType.defaultIndicators());
        List<BigDecimal> sma7 = series.get(IndicatorType.SMA7);
        List<BigDecimal> sma20 = series.get(IndicatorType.SMA20);
        List<BigDecimal> sma50 = series.get(IndicatorType.SMA50);
        List<BigDecimal> rsi14 = series.get(IndicatorType.RSI14);

        // Response yalnızca seçilen aralık içindeki noktaları döner; warm-up barları gizli kalır.
        return IntStream.range(0, history.size())
                .filter(index -> !history.get(index).getPriceTimestamp().isBefore(displayStart))
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
                .orElseThrow(() -> new TechnicalAnalysisException.NotFound(
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
            throw new TechnicalAnalysisException.Validation("interval parameter is required");
        }

        if (!SUPPORTED_INTERVAL.equalsIgnoreCase(interval.trim())) {
            throw new TechnicalAnalysisException.Validation("Only interval=1d is supported");
        }
    }

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "6m";
        }

        String normalized = range.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1m", "3m", "6m", "1y", "max" -> normalized;
            default -> throw new TechnicalAnalysisException.Validation("Unsupported range: " + range);
        };
    }

    /**
     * Gösterge warm-up'ı için veri çekme başlangıcını, görüntülenecek aralığın başından
     * {@link #WARMUP_DAYS} gün öncesine çeker. "max" aralığında zaten en eski kayıttan
     * başlanır; daha geriye gidilecek veri olmadığından warm-up uygulanmaz.
     */
    private Instant resolveWarmupStart(String range, Instant displayStart) {
        if ("max".equals(range)) {
            return displayStart;
        }
        return displayStart.minus(Duration.ofDays(WARMUP_DAYS));
    }

    private SourceName resolveCandleSource(MarketInstrument instrument, String normalizedSymbol) {
        if (instrument.getInstrumentType() == InstrumentType.CRYPTO && instrument.getSourceName() == SourceName.BINANCE) {
            return SourceName.BINANCE;
        }
        if (instrument.getInstrumentType() == InstrumentType.STOCK) {
            return SourceName.YAHOO_FINANCE;
        }
        logger.info("Candlestick data not supported: symbol={}, instrumentType={}, source={}",
                normalizedSymbol, instrument.getInstrumentType(), instrument.getSourceName());
        return null;
    }

    private Instant resolveStartTimestamp(MarketInstrument instrument, SourceName sourceName, String range) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);

        return switch (range) {
            case "1m" -> today.minusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "3m" -> today.minusMonths(3).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "6m" -> today.minusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "1y" -> today.minusYears(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "max" -> marketPriceHistoryRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampAsc(
                            instrument,
                            IntervalType.ONE_DAY,
                            sourceName
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

        // TreeMap, anahtar (priceTimestamp) üzerinden zaten artan sıralı tutar; ayrıca timestamp'e
        // göre tekrar sıralamaya gerek yoktur. Aynı timestamp'te son kayıt önceki(ler)ini ezer.
        Map<Instant, MarketPriceHistory> dedupedByTimestamp = new TreeMap<>();
        for (MarketPriceHistory point : history) {
            if (!hasCompleteOhlc(point)) {
                continue;
            }
            dedupedByTimestamp.put(point.getPriceTimestamp(), point);
        }

        return List.copyOf(dedupedByTimestamp.values());
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
