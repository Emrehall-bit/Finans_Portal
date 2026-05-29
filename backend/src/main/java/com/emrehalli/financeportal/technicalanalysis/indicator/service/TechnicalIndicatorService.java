package com.emrehalli.financeportal.technicalanalysis.indicator.service;

import com.emrehalli.financeportal.technicalanalysis.service.MovingAverageService;
import com.emrehalli.financeportal.technicalanalysis.service.RsiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class TechnicalIndicatorService {

    private static final Logger logger = LogManager.getLogger(TechnicalIndicatorService.class);

    private final MovingAverageService movingAverageService;
    private final RsiService rsiService;

    public TechnicalIndicatorService(MovingAverageService movingAverageService,
                                     RsiService rsiService) {
        this.movingAverageService = movingAverageService;
        this.rsiService = rsiService;
    }

    public List<Double> calculateSMA(List<Double> prices, int period) {
        logger.info("SMA hesaplaniyor: period={}, veriNoktasi={}", period, prices.size());
        validateInputs(prices, period, "SMA");

        List<Double> result = toDoubleList(
                movingAverageService.calculateSimpleMovingAverage(toBigDecimalList(prices), period)
        );
        logger.info("SMA hesaplandi: period={}, sonucNoktasi={}", period, result.stream().filter(v -> v != null).count());
        return result;
    }

    public List<Double> calculateEMA(List<Double> prices, int period) {
        logger.info("EMA hesaplaniyor: period={}, veriNoktasi={}", period, prices.size());
        validateInputs(prices, period, "EMA");

        List<Double> result = new ArrayList<>(prices.size());
        double multiplier = 2.0 / (period + 1);
        Double ema = null;

        for (int i = 0; i < prices.size(); i++) {
            if (i < period - 1) {
                result.add(null);
                continue;
            }
            if (i == period - 1) {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += prices.get(j);
                }
                ema = sum / period;
            } else {
                ema = (prices.get(i) - ema) * multiplier + ema;
            }
            result.add(ema);
        }
        logger.info("EMA hesaplandi: period={}", period);
        return result;
    }

    public List<Double> calculateRSI(List<Double> prices, int period) {
        logger.info("RSI hesaplaniyor: period={}, veriNoktasi={}", period, prices.size());
        validateInputs(prices, period, "RSI");
        if (prices.size() < period + 1) {
            throw new IllegalArgumentException("RSI icin en az " + (period + 1) + " veri noktasi gerekli, mevcut: " + prices.size());
        }

        List<Double> result = toDoubleList(rsiService.calculateRsi(toBigDecimalList(prices), period));
        logger.info("RSI hesaplandi: period={}", period);
        return result;
    }

    public MACDResult calculateMACD(List<Double> prices, int fast, int slow, int signal) {
        logger.info("MACD hesaplaniyor: fast={}, slow={}, signal={}, veriNoktasi={}", fast, slow, signal, prices.size());
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException("Fiyat listesi bos olamaz");
        }
        if (prices.size() < slow + signal) {
            throw new IllegalArgumentException("MACD icin en az " + (slow + signal) + " veri noktasi gerekli, mevcut: " + prices.size());
        }

        List<Double> fastEma = calculateEMA(prices, fast);
        List<Double> slowEma = calculateEMA(prices, slow);

        List<Double> macdLine = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            if (fastEma.get(i) == null || slowEma.get(i) == null) {
                macdLine.add(null);
            } else {
                macdLine.add(fastEma.get(i) - slowEma.get(i));
            }
        }

        List<Double> macdValues = macdLine.stream()
                .map(v -> v == null ? 0.0 : v)
                .toList();
        List<Double> signalLine = calculateEMA(macdValues, signal);

        List<Double> histogram = new ArrayList<>(prices.size());
        for (int i = 0; i < prices.size(); i++) {
            if (macdLine.get(i) == null || signalLine.get(i) == null) {
                histogram.add(null);
            } else {
                histogram.add(macdLine.get(i) - signalLine.get(i));
            }
        }

        logger.info("MACD hesaplandi: fast={}, slow={}, signal={}", fast, slow, signal);
        return new MACDResult(macdLine, signalLine, histogram);
    }

    public BollingerResult calculateBollingerBands(List<Double> prices, int period, double stddev) {
        logger.info("Bollinger Bands hesaplaniyor: period={}, stddev={}, veriNoktasi={}", period, stddev, prices.size());
        validateInputs(prices, period, "Bollinger");

        List<Double> upper = new ArrayList<>(prices.size());
        List<Double> middle = calculateSMA(prices, period);
        List<Double> lower = new ArrayList<>(prices.size());

        for (int i = 0; i < prices.size(); i++) {
            if (middle.get(i) == null) {
                upper.add(null);
                lower.add(null);
                continue;
            }
            double sma = middle.get(i);
            double variance = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = prices.get(j) - sma;
                variance += diff * diff;
            }
            double sd = Math.sqrt(variance / period);
            upper.add(sma + stddev * sd);
            lower.add(sma - stddev * sd);
        }

        logger.info("Bollinger Bands hesaplandi: period={}", period);
        return new BollingerResult(upper, middle, lower);
    }

    public StochasticResult calculateStochastic(List<OhlcvInput> ohlcv, int kPeriod, int dPeriod) {
        logger.info("Stochastic hesaplaniyor: kPeriod={}, dPeriod={}, veriNoktasi={}", kPeriod, dPeriod, ohlcv.size());
        if (ohlcv == null || ohlcv.isEmpty()) {
            throw new IllegalArgumentException("OHLCV verisi bos olamaz");
        }
        if (ohlcv.size() < kPeriod + dPeriod) {
            throw new IllegalArgumentException("Stochastic icin en az " + (kPeriod + dPeriod) + " veri noktasi gerekli");
        }

        List<Double> kLine = new ArrayList<>(ohlcv.size());
        for (int i = 0; i < ohlcv.size(); i++) {
            if (i < kPeriod - 1) {
                kLine.add(null);
                continue;
            }
            double highest = Double.MIN_VALUE;
            double lowest = Double.MAX_VALUE;
            for (int j = i - kPeriod + 1; j <= i; j++) {
                highest = Math.max(highest, ohlcv.get(j).high());
                lowest = Math.min(lowest, ohlcv.get(j).low());
            }
            double range = highest - lowest;
            double k = range == 0 ? 50.0 : ((ohlcv.get(i).close() - lowest) / range) * 100.0;
            kLine.add(k);
        }

        List<Double> kValues = kLine.stream().map(v -> v == null ? 0.0 : v).toList();
        List<Double> dLine = calculateSMA(kValues, dPeriod);

        logger.info("Stochastic hesaplandi: kPeriod={}, dPeriod={}", kPeriod, dPeriod);
        return new StochasticResult(kLine, dLine);
    }

    private void validateInputs(List<Double> prices, int period, String indicator) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException(indicator + " icin fiyat listesi bos olamaz");
        }
        if (period <= 0) {
            throw new IllegalArgumentException(indicator + " icin period sifirdan buyuk olmali");
        }
        if (prices.size() < period) {
            throw new IllegalArgumentException(indicator + " icin en az " + period + " veri noktasi gerekli, mevcut: " + prices.size());
        }
    }

    private List<BigDecimal> toBigDecimalList(List<Double> prices) {
        if (prices == null || prices.isEmpty()) {
            return Collections.emptyList();
        }

        return prices.stream()
                .map(value -> value == null ? null : BigDecimal.valueOf(value))
                .toList();
    }

    private List<Double> toDoubleList(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream()
                .map(value -> value == null ? null : value.doubleValue())
                .toList();
    }

    public record MACDResult(
            List<Double> macdLine,
            List<Double> signalLine,
            List<Double> histogram
    ) {}

    public record BollingerResult(
            List<Double> upper,
            List<Double> middle,
            List<Double> lower
    ) {}

    public record StochasticResult(
            List<Double> kLine,
            List<Double> dLine
    ) {}

    public record OhlcvInput(
            double open,
            double high,
            double low,
            double close,
            double volume
    ) {}
}
