package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class InstrumentComparisonService {

    private static final Logger logger = LogManager.getLogger(InstrumentComparisonService.class);
    private static final BigDecimal BASE_INDEX = BigDecimal.valueOf(100);

    private final HistoricalPriceReader historicalPriceReader;

    public InstrumentComparisonService(HistoricalPriceReader historicalPriceReader) {
        this.historicalPriceReader = historicalPriceReader;
    }

    public ComparisonResponse compare(List<String> symbols, LocalDate from, LocalDate to) {
        logger.info("Technical comparison started: symbolCount={}, from={}, to={}", symbols == null ? 0 : symbols.size(), from, to);

        if (symbols == null || symbols.isEmpty()) {
            throw new TechnicalAnalysisException.Validation("At least 2 symbols are required for comparison");
        }

        List<ComparisonResponse.Series> series = symbols.stream()
                .map(symbol -> buildSeries(symbol, from, to))
                .toList();

        logger.info("Technical comparison completed: symbolCount={}, from={}, to={}", symbols.size(), from, to);
        return new ComparisonResponse(from, to, series);
    }

    private ComparisonResponse.Series buildSeries(String symbol, LocalDate from, LocalDate to) {
        List<HistoricalPricePoint> history = historicalPriceReader.read(symbol, from, to);
        if (history.isEmpty()) {
            logger.warn("Technical comparison has no history: symbol={}, from={}, to={}", symbol, from, to);
            throw new TechnicalAnalysisException.NotFound("Historical price data not found for symbol: " + symbol);
        }

        BigDecimal basePrice = history.getFirst().close();
        if (basePrice == null || BigDecimal.ZERO.compareTo(basePrice) == 0) {
            throw new TechnicalAnalysisException.Validation("Cannot normalize comparison series for symbol: " + symbol);
        }

        List<ComparisonResponse.Point> points = history.stream()
                .map(point -> new ComparisonResponse.Point(
                        point.date(),
                        point.close(),
                        normalize(point.close(), basePrice)
                ))
                .toList();

        return new ComparisonResponse.Series(history.getFirst().symbol(), points);
    }

    private BigDecimal normalize(BigDecimal close, BigDecimal basePrice) {
        if (close == null) {
            return null;
        }

        return close.multiply(BASE_INDEX)
                .divide(basePrice, 8, RoundingMode.HALF_UP);
    }
}



