package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisValidationException;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisPoint;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TechnicalAnalysisService {

    private static final Logger logger = LogManager.getLogger(TechnicalAnalysisService.class);

    private final HistoricalPriceReader historicalPriceReader;
    private final MovingAverageService movingAverageService;
    private final RsiService rsiService;
    private final TrendAnalysisService trendAnalysisService;
    private final InstrumentComparisonService instrumentComparisonService;

    public TechnicalAnalysisService(HistoricalPriceReader historicalPriceReader,
                                    MovingAverageService movingAverageService,
                                    RsiService rsiService,
                                    TrendAnalysisService trendAnalysisService,
                                    InstrumentComparisonService instrumentComparisonService) {
        this.historicalPriceReader = historicalPriceReader;
        this.movingAverageService = movingAverageService;
        this.rsiService = rsiService;
        this.trendAnalysisService = trendAnalysisService;
        this.instrumentComparisonService = instrumentComparisonService;
    }

    public TechnicalAnalysisResult analyze(String symbol, LocalDate from, LocalDate to, String indicators) {
        logger.info("Technical analysis started: symbol={}, from={}, to={}, indicators={}", symbol, from, to, indicators);
        validateSymbol(symbol);
        validateDateRange(from, to);

        Set<IndicatorType> requestedIndicators = resolveIndicators(indicators);
        List<HistoricalPricePoint> history = historicalPriceReader.read(symbol, from, to);
        if (history.isEmpty()) {
            logger.warn("Technical analysis has no history: symbol={}, from={}, to={}", symbol, from, to);
            throw new TechnicalAnalysisNotFoundException("Historical price data not found for symbol: " + symbol);
        }

        List<BigDecimal> closes = history.stream()
                .map(HistoricalPricePoint::close)
                .toList();

        Map<IndicatorType, List<BigDecimal>> indicatorSeries = calculateIndicatorSeries(closes, requestedIndicators);
        List<TechnicalAnalysisPoint> points = buildPoints(history, indicatorSeries);
        TechnicalAnalysisPoint latestPoint = points.getLast();
        TechnicalAnalysisPoint previousPoint = points.size() > 1 ? points.get(points.size() - 2) : null;

        Map<IndicatorType, BigDecimal> latestIndicatorValues = new EnumMap<>(IndicatorType.class);
        for (IndicatorType indicatorType : requestedIndicators) {
            latestIndicatorValues.put(indicatorType, indicatorValue(points, indicatorType));
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
                trendAnalysisService.determineTrend(previousPoint, latestPoint),
                trendAnalysisService.determineSignals(latestPoint),
                Map.copyOf(latestIndicatorValues),
                points
        );
    }

    public ComparisonResult compare(String symbols, LocalDate from, LocalDate to) {
        logger.info("Technical comparison request started: symbols={}, from={}, to={}", symbols, from, to);
        validateDateRange(from, to);

        List<String> requestedSymbols = parseSymbols(symbols);
        if (requestedSymbols.size() < 2) {
            throw new TechnicalAnalysisValidationException("At least 2 symbols are required for comparison");
        }

        return instrumentComparisonService.compare(requestedSymbols, from, to);
    }

    private Map<IndicatorType, List<BigDecimal>> calculateIndicatorSeries(List<BigDecimal> closes, Set<IndicatorType> indicators) {
        Map<IndicatorType, List<BigDecimal>> indicatorSeries = new EnumMap<>(IndicatorType.class);
        for (IndicatorType indicatorType : indicators) {
            indicatorSeries.put(indicatorType, calculateSeries(closes, indicatorType));
        }
        return indicatorSeries;
    }

    private List<TechnicalAnalysisPoint> buildPoints(List<HistoricalPricePoint> history,
                                                     Map<IndicatorType, List<BigDecimal>> indicatorSeries) {
        List<TechnicalAnalysisPoint> points = new ArrayList<>(history.size());
        for (int index = 0; index < history.size(); index++) {
            HistoricalPricePoint point = history.get(index);
            points.add(new TechnicalAnalysisPoint(
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

    private BigDecimal indicatorValue(List<TechnicalAnalysisPoint> points, IndicatorType indicatorType) {
        if (points.isEmpty()) {
            return null;
        }

        TechnicalAnalysisPoint latestPoint = points.getLast();
        return switch (indicatorType) {
            case SMA7 -> latestPoint.sma7();
            case SMA20 -> latestPoint.sma20();
            case SMA50 -> latestPoint.sma50();
            case RSI14 -> latestPoint.rsi14();
        };
    }

    private List<BigDecimal> calculateSeries(List<BigDecimal> closes, IndicatorType indicatorType) {
        return switch (indicatorType) {
            case SMA7, SMA20, SMA50 -> movingAverageService.calculateSimpleMovingAverage(closes, indicatorType.period());
            case RSI14 -> rsiService.calculateRsi(closes, indicatorType.period());
        };
    }

    private BigDecimal valueAt(List<BigDecimal> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private Set<IndicatorType> resolveIndicators(String indicators) {
        Set<IndicatorType> resolvedIndicators = new LinkedHashSet<>();
        resolvedIndicators.add(IndicatorType.SMA7);
        resolvedIndicators.add(IndicatorType.SMA20);

        if (indicators == null || indicators.isBlank()) {
            resolvedIndicators.add(IndicatorType.SMA50);
            resolvedIndicators.add(IndicatorType.RSI14);
            return Set.copyOf(resolvedIndicators);
        }

        for (String rawIndicator : indicators.split(",")) {
            IndicatorType indicatorType = IndicatorType.fromValue(rawIndicator)
                    .orElseThrow(() -> new TechnicalAnalysisValidationException("Unsupported indicator: " + rawIndicator));
            resolvedIndicators.add(indicatorType);
        }

        return Set.copyOf(resolvedIndicators);
    }

    private List<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            throw new TechnicalAnalysisValidationException("symbols parameter cannot be blank");
        }

        List<String> parsedSymbols = java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        if (parsedSymbols.isEmpty()) {
            throw new TechnicalAnalysisValidationException("symbols parameter cannot be blank");
        }

        return parsedSymbols;
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new TechnicalAnalysisValidationException("symbol cannot be blank");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new TechnicalAnalysisValidationException("from parameter is required");
        }

        if (to == null) {
            throw new TechnicalAnalysisValidationException("to parameter is required");
        }

        if (from.isAfter(to)) {
            throw new TechnicalAnalysisValidationException("from cannot be after to");
        }
    }
}
