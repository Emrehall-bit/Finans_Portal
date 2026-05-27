package com.emrehalli.financeportal.analysis.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TechnicalIndicatorService {

    private static final Logger logger = LogManager.getLogger(TechnicalIndicatorService.class);

    // ── SMA ──────────────────────────────────────────────────────────────────

    public List<Double> calculateSMA(List<Double> prices, int period) {
        logger.info("SMA hesaplanıyor: period={}, veriNoktası={}", period, prices.size());
        validateInputs(prices, period, "SMA");

        List<Double> result = new ArrayList<>(prices.size());
        double rollingSum = 0.0;

        for (int i = 0; i < prices.size(); i++) {
            rollingSum += prices.get(i);
            if (i >= period) {
                rollingSum -= prices.get(i - period);
            }
            if (i < period - 1) {
                result.add(null);
            } else {
                result.add(rollingSum / period);
            }
        }
        logger.info("SMA hesaplandı: period={}, sonuçNoktası={}", period, result.stream().filter(v -> v != null).count());
        return result;
    }

    // ── EMA ──────────────────────────────────────────────────────────────────

    public List<Double> calculateEMA(List<Double> prices, int period) {
        logger.info("EMA hesaplanıyor: period={}, veriNoktası={}", period, prices.size());
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
                // İlk EMA değeri: ilk 'period' kapanışın ortalaması
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
        logger.info("EMA hesaplandı: period={}", period);
        return result;
    }

    // ── RSI ──────────────────────────────────────────────────────────────────

    public List<Double> calculateRSI(List<Double> prices, int period) {
        logger.info("RSI hesaplanıyor: period={}, veriNoktası={}", period, prices.size());
        validateInputs(prices, period, "RSI");
        if (prices.size() < period + 1) {
            throw new IllegalArgumentException("RSI için en az " + (period + 1) + " veri noktası gerekli, mevcut: " + prices.size());
        }

        List<Double> result = new ArrayList<>(prices.size());

        // İlk period için null ekle
        for (int i = 0; i < period; i++) {
            result.add(null);
        }

        // İlk ortalama gain/loss (Wilder yöntemi)
        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        double rs = avgLoss == 0 ? 100.0 : avgGain / avgLoss;
        result.add(100.0 - (100.0 / (1.0 + rs)));

        // Sonraki değerler — Wilder smoothing
        for (int i = period + 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            rs = avgLoss == 0 ? 100.0 : avgGain / avgLoss;
            result.add(100.0 - (100.0 / (1.0 + rs)));
        }

        logger.info("RSI hesaplandı: period={}", period);
        return result;
    }

    // ── MACD ─────────────────────────────────────────────────────────────────

    public MACDResult calculateMACD(List<Double> prices, int fast, int slow, int signal) {
        logger.info("MACD hesaplanıyor: fast={}, slow={}, signal={}, veriNoktası={}", fast, slow, signal, prices.size());
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException("Fiyat listesi boş olamaz");
        }
        if (prices.size() < slow + signal) {
            throw new IllegalArgumentException("MACD için en az " + (slow + signal) + " veri noktası gerekli, mevcut: " + prices.size());
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

        // Signal line: EMA of MACD line (skip nulls)
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

        logger.info("MACD hesaplandı: fast={}, slow={}, signal={}", fast, slow, signal);
        return new MACDResult(macdLine, signalLine, histogram);
    }

    // ── Bollinger Bands ───────────────────────────────────────────────────────

    public BollingerResult calculateBollingerBands(List<Double> prices, int period, double stddev) {
        logger.info("Bollinger Bands hesaplanıyor: period={}, stddev={}, veriNoktası={}", period, stddev, prices.size());
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

        logger.info("Bollinger Bands hesaplandı: period={}", period);
        return new BollingerResult(upper, middle, lower);
    }

    // ── Stochastic ────────────────────────────────────────────────────────────

    public StochasticResult calculateStochastic(List<OhlcvInput> ohlcv, int kPeriod, int dPeriod) {
        logger.info("Stochastic hesaplanıyor: kPeriod={}, dPeriod={}, veriNoktası={}", kPeriod, dPeriod, ohlcv.size());
        if (ohlcv == null || ohlcv.isEmpty()) {
            throw new IllegalArgumentException("OHLCV verisi boş olamaz");
        }
        if (ohlcv.size() < kPeriod + dPeriod) {
            throw new IllegalArgumentException("Stochastic için en az " + (kPeriod + dPeriod) + " veri noktası gerekli");
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

        // D line: SMA of K line
        List<Double> kValues = kLine.stream().map(v -> v == null ? 0.0 : v).toList();
        List<Double> dLine = calculateSMA(kValues, dPeriod);

        logger.info("Stochastic hesaplandı: kPeriod={}, dPeriod={}", kPeriod, dPeriod);
        return new StochasticResult(kLine, dLine);
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────────

    private void validateInputs(List<Double> prices, int period, String indicator) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException(indicator + " için fiyat listesi boş olamaz");
        }
        if (period <= 0) {
            throw new IllegalArgumentException(indicator + " için period sıfırdan büyük olmalı");
        }
        if (prices.size() < period) {
            throw new IllegalArgumentException(indicator + " için en az " + period + " veri noktası gerekli, mevcut: " + prices.size());
        }
    }

    // ── İç record'lar ─────────────────────────────────────────────────────────

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
