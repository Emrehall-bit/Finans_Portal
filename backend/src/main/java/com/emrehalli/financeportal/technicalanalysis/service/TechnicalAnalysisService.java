package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TechnicalAnalysisService {

    private static final Logger logger = LogManager.getLogger(TechnicalAnalysisService.class);
    private static final int REQUIRED_HISTORY_POINT_COUNT = 60;

    private final HistoricalPriceReader historicalPriceReader;
    private final IndicatorSeriesCalculator indicatorSeriesCalculator;
    private final TrendAnalysisService trendAnalysisService;
    private final InstrumentComparisonService instrumentComparisonService;
    private final AppMessageSource appMessageSource;

    public TechnicalAnalysisService(HistoricalPriceReader historicalPriceReader,
                                    IndicatorSeriesCalculator indicatorSeriesCalculator,
                                    TrendAnalysisService trendAnalysisService,
                                    InstrumentComparisonService instrumentComparisonService,
                                    AppMessageSource appMessageSource) {
        this.historicalPriceReader = historicalPriceReader;
        this.indicatorSeriesCalculator = indicatorSeriesCalculator;
        this.trendAnalysisService = trendAnalysisService;
        this.instrumentComparisonService = instrumentComparisonService;
        this.appMessageSource = appMessageSource;
    }

    @Cacheable(value = "technicalAnalysis", key = "T(com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisInputs).analysisCacheKey(#symbol, #from, #to, #indicators)")
    public TechnicalAnalysisResult analyze(String symbol, LocalDate from, LocalDate to, String indicators) {
        logger.info("Technical analysis started: symbol={}, from={}, to={}, indicators={}", symbol, from, to, indicators);
        TechnicalAnalysisInputs.validateSymbol(symbol);
        validateDateRange(from, to);

        Set<IndicatorType> requestedIndicators = resolveIndicators(indicators);
        List<HistoricalPricePoint> history = filterIncompleteCloses(historicalPriceReader.read(symbol, from, to));
        if (history.isEmpty()) {
            throw new TechnicalAnalysisException.NotFound("Historical price data not found for symbol: " + symbol);
        }
        int requiredPointCount = REQUIRED_HISTORY_POINT_COUNT;
        if (history.size() < requiredPointCount) {
            logger.warn("Technical analysis has insufficient history: symbol={}, from={}, to={}, pointCount={}, requiredPointCount={}",
                    symbol, from, to, history.size(), requiredPointCount);
            List<TechnicalAnalysisResult.Point> points = buildPoints(history, Map.of());
            return new TechnicalAnalysisResult(
                    symbol,
                    from,
                    to,
                    history.isEmpty() ? null : history.getLast().close(),
                    "INSUFFICIENT_HISTORY",
                    appMessageSource.get("technical.analysis.insufficientHistory"),
                    com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection.SIDEWAYS,
                    List.of(),
                    Map.of(),
                    points
            );
        }

        List<BigDecimal> closes = history.stream()
                .map(HistoricalPricePoint::close)
                .toList();

        Map<IndicatorType, List<BigDecimal>> indicatorSeries = indicatorSeriesCalculator.calculate(closes, requestedIndicators);
        List<TechnicalAnalysisResult.Point> points = buildPoints(history, indicatorSeries);
        TechnicalAnalysisResult.Point latestPoint = points.getLast();

        Map<IndicatorType, BigDecimal> latestIndicatorValues = new EnumMap<>(IndicatorType.class);
        for (IndicatorType indicatorType : requestedIndicators) {
            BigDecimal indicatorValue = indicatorValue(points, indicatorType);
            if (indicatorValue != null) {
                latestIndicatorValues.put(indicatorType, indicatorValue);
            }
        }

        logger.info(
                "Technical analysis completed: symbol={}, from={}, to={}, pointCount={}, indicators={}",
                symbol,
                from,
                to,
                points.size(),
                requestedIndicators
        );

        return new TechnicalAnalysisResult(
                history.getFirst().symbol(),
                from,
                to,
                latestPoint.close(),
                "AVAILABLE",
                null,
                trendAnalysisService.determineTrend(points),
                trendAnalysisService.determineSignals(latestPoint),
                latestIndicatorValues.isEmpty() ? Map.of() : Map.copyOf(latestIndicatorValues),
                points
        );
    }

    @Cacheable(value = "instrumentComparison", key = "T(com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisInputs).comparisonCacheKey(#symbols, #from, #to)")
    public ComparisonResult compare(String symbols, LocalDate from, LocalDate to) {
        logger.info("Technical comparison request started: symbols={}, from={}, to={}", symbols, from, to);
        validateDateRange(from, to);

        List<String> requestedSymbols = parseSymbols(symbols);
        if (requestedSymbols.size() < 2) {
            throw new TechnicalAnalysisException.Validation("At least 2 symbols are required for comparison");
        }
        for (String sym : requestedSymbols) {
            TechnicalAnalysisInputs.validateSymbol(sym);
        }

        return instrumentComparisonService.compare(requestedSymbols, from, to);
    }

    private List<TechnicalAnalysisResult.Point> buildPoints(List<HistoricalPricePoint> history,
                                                     Map<IndicatorType, List<BigDecimal>> indicatorSeries) {
        List<TechnicalAnalysisResult.Point> points = new ArrayList<>(history.size());
        for (int index = 0; index < history.size(); index++) {
            HistoricalPricePoint point = history.get(index);
            points.add(new TechnicalAnalysisResult.Point(
                    point.date(),
                    point.close(),
                    valueAt(indicatorSeries.get(IndicatorType.SMA7), index),
                    valueAt(indicatorSeries.get(IndicatorType.SMA20), index),
                    valueAt(indicatorSeries.get(IndicatorType.SMA50), index),
                    valueAt(indicatorSeries.get(IndicatorType.RSI14), index)
            ));
        }
        return List.copyOf(points);
    }

    private BigDecimal indicatorValue(List<TechnicalAnalysisResult.Point> points, IndicatorType indicatorType) {
        if (points.isEmpty()) {
            return null;
        }

        TechnicalAnalysisResult.Point latestPoint = points.getLast();
        return switch (indicatorType) {
            case SMA7 -> latestPoint.sma7();
            case SMA20 -> latestPoint.sma20();
            case SMA50 -> latestPoint.sma50();
            case RSI14 -> latestPoint.rsi14();
        };
    }

    private BigDecimal valueAt(List<BigDecimal> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private Set<IndicatorType> resolveIndicators(String indicators) {
        if (indicators == null || indicators.isBlank()) {
            return IndicatorType.defaultIndicators();
        }

        // SMA7 ve SMA20 trend/sinyal hesabı için her zaman gereklidir; kullanıcı seçimine eklenir.
        Set<IndicatorType> resolvedIndicators = new LinkedHashSet<>();
        resolvedIndicators.add(IndicatorType.SMA7);
        resolvedIndicators.add(IndicatorType.SMA20);

        for (String rawIndicator : indicators.split(",")) {
            IndicatorType indicatorType = IndicatorType.fromValue(rawIndicator)
                    .orElseThrow(() -> new TechnicalAnalysisException.Validation("Unsupported indicator: " + rawIndicator));
            resolvedIndicators.add(indicatorType);
        }

        return Set.copyOf(resolvedIndicators);
    }

    private List<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            throw new TechnicalAnalysisException.Validation("symbols parameter cannot be blank");
        }

        List<String> parsedSymbols = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        if (parsedSymbols.isEmpty()) {
            throw new TechnicalAnalysisException.Validation("symbols parameter cannot be blank");
        }

        return parsedSymbols;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new TechnicalAnalysisException.Validation("from parameter is required");
        }

        if (to == null) {
            throw new TechnicalAnalysisException.Validation("to parameter is required");
        }

        if (from.isAfter(to)) {
            throw new TechnicalAnalysisException.Validation("from cannot be after to");
        }
    }

    private List<HistoricalPricePoint> filterIncompleteCloses(List<HistoricalPricePoint> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        return history.stream()
                .filter(point -> point != null && point.close() != null)
                .toList();
    }
}




