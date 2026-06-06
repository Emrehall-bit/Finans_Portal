package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.entity.MacroObservation;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import com.emrehalli.financeportal.market.persistence.MacroObservationRepository;
import com.emrehalli.financeportal.technicalanalysis.dto.BenchmarkResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BenchmarkComparisonService {

    private static final Logger logger = LogManager.getLogger(BenchmarkComparisonService.class);
    private static final BigDecimal BASE_INDEX = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final HistoricalPriceReader historicalPriceReader;
    private final MacroObservationRepository macroObservationRepository;

    public BenchmarkComparisonService(HistoricalPriceReader historicalPriceReader,
                                      MacroObservationRepository macroObservationRepository) {
        this.historicalPriceReader = historicalPriceReader;
        this.macroObservationRepository = macroObservationRepository;
    }

    public BenchmarkResponse compare(String baseCode, String benchmarkCode,
                                     String benchmarkType, LocalDate from, LocalDate to) {
        logger.info("Benchmark comparison: base={}, benchmark={}, type={}, from={}, to={}",
                baseCode, benchmarkCode, benchmarkType, from, to);

        ComparisonResponse.Series baseSeries = buildInstrumentSeries(baseCode, from, to);

        ComparisonResponse.Series benchmarkSeries;
        boolean isMacro = "MACRO".equalsIgnoreCase(benchmarkType);
        if (isMacro) {
            benchmarkSeries = buildMacroSeries(benchmarkCode, from, to);
        } else {
            benchmarkSeries = buildInstrumentSeries(benchmarkCode, from, to);
        }

        BigDecimal baseReturn = computeReturn(baseSeries);
        BigDecimal benchmarkReturn = computeReturn(benchmarkSeries);

        BigDecimal relativeReturn = isMacro ? null : baseReturn.subtract(benchmarkReturn);
        BigDecimal realReturn = isMacro ? computeRealReturn(baseReturn, benchmarkReturn) : null;

        BigDecimal effectiveReturn = isMacro ? realReturn : relativeReturn;
        String status;
        if (isMacro) {
            status = effectiveReturn != null && effectiveReturn.compareTo(BigDecimal.ZERO) >= 0
                    ? "ABOVE_INFLATION" : "BELOW_INFLATION";
        } else {
            status = effectiveReturn != null && effectiveReturn.compareTo(BigDecimal.ZERO) >= 0
                    ? "ABOVE_BENCHMARK" : "BELOW_BENCHMARK";
        }

        return new BenchmarkResponse(from, to, baseCode, benchmarkCode, benchmarkType,
                baseReturn, benchmarkReturn, relativeReturn, realReturn, status,
                List.of(baseSeries, benchmarkSeries));
    }

    private ComparisonResponse.Series buildInstrumentSeries(String symbol, LocalDate from, LocalDate to) {
        List<HistoricalPricePoint> history = historicalPriceReader.read(symbol, from, to);
        if (history.isEmpty()) {
            throw new TechnicalAnalysisException.NotFound("Historical data not found for symbol: " + symbol);
        }
        BigDecimal basePrice = history.getFirst().close();
        if (basePrice == null || BigDecimal.ZERO.compareTo(basePrice) == 0) {
            throw new TechnicalAnalysisException.Validation("Cannot normalize series for symbol: " + symbol);
        }
        List<ComparisonResponse.Point> points = history.stream()
                .map(p -> new ComparisonResponse.Point(p.date(), p.close(),
                        normalize(p.close(), basePrice)))
                .toList();
        return new ComparisonResponse.Series(history.getFirst().symbol(), points);
    }

    private ComparisonResponse.Series buildMacroSeries(String code, LocalDate from, LocalDate to) {
        List<MacroObservation> observations = macroObservationRepository
                .findByIndicatorCodeAndValueTypeAndObservationDateBetweenOrderByObservationDateAsc(
                        code, MacroValueType.MONTHLY_CHANGE, from, to);

        if (observations.isEmpty()) {
            throw new TechnicalAnalysisException.NotFound("Macro data not found for indicator: " + code);
        }

        List<ComparisonResponse.Point> points = new ArrayList<>();
        BigDecimal cumulative = BASE_INDEX;
        points.add(new ComparisonResponse.Point(from, null, cumulative));
        for (MacroObservation obs : observations) {
            BigDecimal monthlyFactor = BigDecimal.ONE
                    .add(obs.getValue().divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
            cumulative = cumulative.multiply(monthlyFactor).setScale(4, RoundingMode.HALF_UP);
            points.add(new ComparisonResponse.Point(obs.getObservationDate(), obs.getValue(), cumulative));
        }

        return new ComparisonResponse.Series(code, points);
    }

    private BigDecimal normalize(BigDecimal close, BigDecimal basePrice) {
        if (close == null) return null;
        return close.multiply(BASE_INDEX).divide(basePrice, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal computeReturn(ComparisonResponse.Series series) {
        List<ComparisonResponse.Point> points = series.points();
        if (points.isEmpty()) return BigDecimal.ZERO;
        BigDecimal first = points.getFirst().normalizedValue();
        BigDecimal last = points.getLast().normalizedValue();
        if (first == null || BigDecimal.ZERO.compareTo(first) == 0) return BigDecimal.ZERO;
        return last.subtract(first).divide(first, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal computeRealReturn(BigDecimal nominalReturn, BigDecimal inflationReturn) {
        BigDecimal denominator = BigDecimal.ONE.add(inflationReturn);
        if (BigDecimal.ZERO.compareTo(denominator) == 0) return null;
        return BigDecimal.ONE.add(nominalReturn)
                .divide(denominator, 6, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
    }
}
