package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformancePointDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioRiskMetricDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioRiskSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PortfolioRiskAnalysisService {

    private static final String INTERNAL_CASH_CODE = "TRY";
    private static final int VOLATILITY_LOOKBACK_DAYS = 180;
    private static final int LIQUIDITY_LOOKBACK_DAYS = 30;

    private final PortfolioHoldingService portfolioHoldingService;
    private final PortfolioPerformanceHistoryService portfolioPerformanceHistoryService;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;

    public PortfolioRiskAnalysisService(PortfolioHoldingService portfolioHoldingService,
                                        PortfolioPerformanceHistoryService portfolioPerformanceHistoryService,
                                        MarketInstrumentRepository marketInstrumentRepository,
                                        MarketPriceHistoryRepository marketPriceHistoryRepository) {
        this.portfolioHoldingService = portfolioHoldingService;
        this.portfolioPerformanceHistoryService = portfolioPerformanceHistoryService;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketPriceHistoryRepository = marketPriceHistoryRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioRiskSummaryResponse getRiskSummary(Long portfolioId) {
        PortfolioValuationResult valuation = portfolioHoldingService.getPortfolioValuation(portfolioId);
        List<PositionContext> positions = buildPositionContexts(valuation.holdings());

        PortfolioRiskMetricDto diversification = buildDiversificationMetric(positions);
        PortfolioRiskMetricDto volatility = buildVolatilityMetric(portfolioId);
        PortfolioRiskMetricDto liquidity = buildLiquidityMetric(positions);
        RiskAlert alert = buildRiskAlert(positions, diversification, volatility, liquidity);

        return new PortfolioRiskSummaryResponse(
                diversification,
                volatility,
                liquidity,
                alert.title(),
                alert.message(),
                alert.tone(),
                VOLATILITY_LOOKBACK_DAYS
        );
    }

    private List<PositionContext> buildPositionContexts(List<PortfolioHoldingDto> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }

        Map<String, AggregatedPosition> aggregated = new LinkedHashMap<>();
        for (PortfolioHoldingDto holding : holdings) {
            String code = normalizeCode(holding.getInstrumentCode());
            AggregatedPosition bucket = aggregated.computeIfAbsent(code, ignored -> new AggregatedPosition(code, resolveInstrumentType(code)));
            bucket.quantity = bucket.quantity.add(zeroIfNull(holding.getQuantity()));
            bucket.positionValue = bucket.positionValue.add(resolvePositionValue(holding));
            if (holding.getCurrentPrice() != null && holding.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                bucket.currentPrice = holding.getCurrentPrice();
            } else if (bucket.currentPrice == null && holding.getBuyPrice() != null && holding.getBuyPrice().compareTo(BigDecimal.ZERO) > 0) {
                bucket.currentPrice = holding.getBuyPrice();
            }
        }

        BigDecimal totalValue = aggregated.values().stream()
                .map(position -> position.positionValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return aggregated.values().stream()
                .map(position -> position.toContext(totalValue))
                .sorted(Comparator.comparing(PositionContext::weight).reversed())
                .toList();
    }

    private PortfolioRiskMetricDto buildDiversificationMetric(List<PositionContext> positions) {
        if (positions.isEmpty()) {
            return new PortfolioRiskMetricDto("NÃ¶tr", 0, BigDecimal.ZERO, "0 etkin pozisyon", "PortfÃ¶y daÄŸÄ±lÄ±mÄ± bulunamadÄ±.");
        }

        BigDecimal holdingHhi = BigDecimal.ZERO;
        Map<String, BigDecimal> weightsByType = new LinkedHashMap<>();
        for (PositionContext position : positions) {
            BigDecimal weightRatio = position.weight().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            holdingHhi = holdingHhi.add(weightRatio.multiply(weightRatio));
            weightsByType.merge(position.instrumentType().name(), position.weight(), BigDecimal::add);
        }

        int holdingCount = positions.size();
        double holdingScore = normalizeConcentrationScore(holdingHhi.doubleValue(), holdingCount);
        BigDecimal typeHhi = BigDecimal.ZERO;
        for (BigDecimal typeWeight : weightsByType.values()) {
            BigDecimal ratio = typeWeight.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            typeHhi = typeHhi.add(ratio.multiply(ratio));
        }
        double typeScore = normalizeConcentrationScore(typeHhi.doubleValue(), Math.max(1, weightsByType.size()));
        int finalScore = clampScore((int) Math.round(holdingScore * 0.75 + typeScore * 0.25));
        BigDecimal effectivePositions = BigDecimal.ONE.divide(holdingHhi.max(BigDecimal.valueOf(0.00000001D)), 2, RoundingMode.HALF_UP);

        return new PortfolioRiskMetricDto(
                labelByScore(finalScore, "ZayÄ±f", "Orta", "Ä°yi"),
                finalScore,
                effectivePositions,
                effectivePositions.stripTrailingZeros().toPlainString() + " etkin pozisyon",
                "Mevcut portfÃ¶y aÄŸÄ±rlÄ±klarÄ± Ã¼zerinden yoÄŸunlaÅŸma (HHI) ve varlÄ±k tÃ¼rÃ¼ daÄŸÄ±lÄ±mÄ± birlikte deÄŸerlendirildi."
        );
    }

    private PortfolioRiskMetricDto buildVolatilityMetric(Long portfolioId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(VOLATILITY_LOOKBACK_DAYS - 1L);
        List<PortfolioPerformancePointDto> points = portfolioPerformanceHistoryService
                .getPerformanceHistory(portfolioId, from, to)
                .points();

        List<Double> returns = new ArrayList<>();
        BigDecimal previousValue = null;
        for (PortfolioPerformancePointDto point : points) {
            if (point.totalValue() == null || point.totalValue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                double dailyReturn = point.totalValue()
                        .subtract(previousValue)
                        .divide(previousValue, 8, RoundingMode.HALF_UP)
                        .doubleValue();
                returns.add(dailyReturn);
            }
            previousValue = point.totalValue();
        }

        if (returns.size() < 10) {
            return new PortfolioRiskMetricDto("SÄ±nÄ±rlÄ± veri", 0, BigDecimal.ZERO, "Yeterli geÃ§miÅŸ yok", "GÃ¼nlÃ¼k portfÃ¶y serisi volatilite hesabÄ± iÃ§in yeterli deÄŸil.");
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2D))
                .sum() / Math.max(1, returns.size() - 1);
        double annualizedVolatility = Math.sqrt(variance) * Math.sqrt(252D) * 100D;
        int score = clampScore((int) Math.round(Math.min(100D, annualizedVolatility / 0.60D)));
        BigDecimal rawValue = BigDecimal.valueOf(annualizedVolatility).setScale(2, RoundingMode.HALF_UP);

        return new PortfolioRiskMetricDto(
                labelByScore(score, "DÃ¼ÅŸÃ¼k", "Orta", "YÃ¼ksek"),
                score,
                rawValue,
                "%" + rawValue.stripTrailingZeros().toPlainString() + " yÄ±llÄ±klaÅŸtÄ±rÄ±lmÄ±ÅŸ",
                "Son " + VOLATILITY_LOOKBACK_DAYS + " gÃ¼nÃ¼n portfÃ¶y gÃ¼nlÃ¼k getirilerinden yÄ±llÄ±klaÅŸtÄ±rÄ±lmÄ±ÅŸ standart sapma hesaplandÄ±."
        );
    }

    private PortfolioRiskMetricDto buildLiquidityMetric(List<PositionContext> positions) {
        if (positions.isEmpty()) {
            return new PortfolioRiskMetricDto("NÃ¶tr", 0, BigDecimal.ZERO, "Veri yok", "PortfÃ¶y pozisyonu bulunamadÄ±.");
        }

        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal weightedCoverage = BigDecimal.ZERO;
        BigDecimal totalValue = positions.stream().map(PositionContext::positionValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        for (PositionContext position : positions) {
            BigDecimal weightRatio = position.weight().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            LiquidityResult result = resolveLiquidity(position);
            weightedScore = weightedScore.add(BigDecimal.valueOf(result.score()).multiply(weightRatio));
            if (result.volumeBacked()) {
                weightedCoverage = weightedCoverage.add(position.weight());
            }
        }

        int score = clampScore(weightedScore.setScale(0, RoundingMode.HALF_UP).intValue());
        BigDecimal rawCoverage = totalValue.compareTo(BigDecimal.ZERO) > 0
                ? weightedCoverage.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new PortfolioRiskMetricDto(
                labelByScore(score, "ZayÄ±f", "Orta", "Ä°yi"),
                score,
                rawCoverage,
                "%" + rawCoverage.stripTrailingZeros().toPlainString() + " hacim destekli kapsam",
                "PozisyonlarÄ±n son " + LIQUIDITY_LOOKBACK_DAYS + " gÃ¼nlÃ¼k iÅŸlem hacmi, fiyat ve pozisyon bÃ¼yÃ¼klÃ¼ÄŸÃ¼ karÅŸÄ±laÅŸtÄ±rÄ±ldÄ±; hacim olmayan varlÄ±klarda sÄ±nÄ±rlÄ± tip fallback kullanÄ±ldÄ±."
        );
    }

    private LiquidityResult resolveLiquidity(PositionContext position) {
        if (INTERNAL_CASH_CODE.equalsIgnoreCase(position.instrumentCode())) {
            return new LiquidityResult(100, true);
        }

        Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository.findByInstrumentCodeIgnoreCase(position.instrumentCode());
        if (instrumentOptional.isEmpty()) {
            return new LiquidityResult(fallbackLiquidityScore(position.instrumentType()), false);
        }

        Instant from = LocalDate.now().minusDays(LIQUIDITY_LOOKBACK_DAYS).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<MarketPriceHistory> history = marketPriceHistoryRepository.findByInstrumentIdAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                instrumentOptional.get().getId(),
                from,
                to
        );

        List<BigDecimal> notionals = history.stream()
                .filter(item -> item.getVolume() != null && item.getClosePrice() != null)
                .map(item -> item.getVolume().multiply(item.getClosePrice()))
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (notionals.isEmpty() || position.positionValue().compareTo(BigDecimal.ZERO) <= 0) {
            return new LiquidityResult(fallbackLiquidityScore(position.instrumentType()), false);
        }

        BigDecimal averageNotional = notionals.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(notionals.size()), 8, RoundingMode.HALF_UP);
        BigDecimal turnoverRatio = averageNotional.divide(position.positionValue(), 8, RoundingMode.HALF_UP);
        double ratio = turnoverRatio.doubleValue();

        if (ratio >= 20D) {
            return new LiquidityResult(100, true);
        }
        if (ratio >= 10D) {
            return new LiquidityResult(92, true);
        }
        if (ratio >= 5D) {
            return new LiquidityResult(82, true);
        }
        if (ratio >= 2D) {
            return new LiquidityResult(68, true);
        }
        if (ratio >= 1D) {
            return new LiquidityResult(54, true);
        }
        if (ratio >= 0.25D) {
            return new LiquidityResult(36, true);
        }
        return new LiquidityResult(20, true);
    }

    private RiskAlert buildRiskAlert(List<PositionContext> positions,
                                     PortfolioRiskMetricDto diversification,
                                     PortfolioRiskMetricDto volatility,
                                     PortfolioRiskMetricDto liquidity) {
        PositionContext topPosition = positions.isEmpty() ? null : positions.getFirst();
        if (topPosition != null && topPosition.weight().compareTo(BigDecimal.valueOf(55)) >= 0) {
            return new RiskAlert(
                    "YÃ¼ksek konsantrasyon riski",
                    topPosition.instrumentCode() + " portfÃ¶yÃ¼n %" + topPosition.weight().setScale(1, RoundingMode.HALF_UP).toPlainString() + "'ini oluÅŸturuyor. DaÄŸÄ±lÄ±mÄ± dengelemek riski azaltÄ±r.",
                    "warning"
            );
        }
        if (volatility.score() >= 65 && liquidity.score() < 45) {
            return new RiskAlert(
                    "Volatilite yÃ¼ksek, likidite sÄ±nÄ±rlÄ±",
                    "PortfÃ¶y oynaklÄ±ÄŸÄ± yÃ¼kselirken iÅŸlem hacmi desteÄŸi zayÄ±f kalÄ±yor. Pozisyon boyutlarÄ± ve nakit tamponu gÃ¶zden geÃ§irilmeli.",
                    "warning"
            );
        }
        if (diversification.score() >= 70 && liquidity.score() >= 60) {
            return new RiskAlert(
                    "DaÄŸÄ±lÄ±m ve likidite dengeli",
                    "PortfÃ¶y farklÄ± varlÄ±klara yayÄ±lmÄ±ÅŸ ve likidite gÃ¶rÃ¼nÃ¼mÃ¼ destekleyici. Mevcut Ã§erÃ§eve korunabilir.",
                    "positive"
            );
        }
        return new RiskAlert(
                "Risk gÃ¶rÃ¼nÃ¼mÃ¼ dengeli",
                "PortfÃ¶y temel metriklerde izlenebilir seviyede. BÃ¼yÃ¼k aÄŸÄ±rlÄ±k deÄŸiÅŸimlerinde volatilite ve likidite birlikte takip edilmeli.",
                "neutral"
        );
    }

    private InstrumentType resolveInstrumentType(String instrumentCode) {
        if (INTERNAL_CASH_CODE.equalsIgnoreCase(instrumentCode)) {
            return null;
        }
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCase(instrumentCode)
                .map(MarketInstrument::getInstrumentType)
                .orElse(null);
    }

    private BigDecimal resolvePositionValue(PortfolioHoldingDto holding) {
        if (holding.getCurrentValue() != null && holding.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
            return holding.getCurrentValue();
        }
        return zeroIfNull(holding.getBuyPrice()).multiply(zeroIfNull(holding.getQuantity()));
    }

    private String normalizeCode(String instrumentCode) {
        return instrumentCode == null ? "" : instrumentCode.trim().toUpperCase();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int fallbackLiquidityScore(InstrumentType instrumentType) {
        if (instrumentType == null) {
            return 100;
        }
        return switch (instrumentType) {
            case FX -> 85;
            case CRYPTO -> 78;
            case FUND -> 58;
            case STOCK -> 46;
            case INDEX -> 72;
            case BOND -> 64;
            case COMMODITY -> 54;
            case FUTURES -> 62;
        };
    }

    private String labelByScore(int score, String low, String medium, String high) {
        if (score >= 70) {
            return high;
        }
        if (score >= 45) {
            return medium;
        }
        return low;
    }

    private double normalizeConcentrationScore(double hhi, int count) {
        if (count <= 1) {
            return 0D;
        }
        double minHhi = 1D / count;
        double normalized = (hhi - minHhi) / (1D - minHhi);
        return Math.max(0D, Math.min(100D, (1D - normalized) * 100D));
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static final class AggregatedPosition {
        private final String instrumentCode;
        private final InstrumentType instrumentType;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal positionValue = BigDecimal.ZERO;
        private BigDecimal currentPrice;

        private AggregatedPosition(String instrumentCode, InstrumentType instrumentType) {
            this.instrumentCode = instrumentCode;
            this.instrumentType = instrumentType;
        }

        private PositionContext toContext(BigDecimal totalValue) {
            BigDecimal weight = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? positionValue.multiply(BigDecimal.valueOf(100)).divide(totalValue, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new PositionContext(instrumentCode, instrumentType, quantity, currentPrice, positionValue, weight);
        }
    }

    private record PositionContext(
            String instrumentCode,
            InstrumentType instrumentType,
            BigDecimal quantity,
            BigDecimal currentPrice,
            BigDecimal positionValue,
            BigDecimal weight
    ) {
    }

    private record LiquidityResult(int score, boolean volumeBacked) {
    }

    private record RiskAlert(String title, String message, String tone) {
    }
}




