package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisContext.PositionSnapshot;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class PortfolioWeightCalculator {

    List<PositionSnapshot> buildPositions(List<PortfolioHoldingDto> holdings, BigDecimal totalValue) {
        return holdings.stream()
                .map(holding -> {
                    String symbol = normalizeSymbol(holding.getInstrumentCode());
                    String instrumentType = resolveInstrumentType(symbol);
                    BigDecimal weightPercent = null;
                    if (totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0
                            && holding.isValuationAvailable() && holding.getCurrentValue() != null) {
                        weightPercent = holding.getCurrentValue()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalValue, 4, RoundingMode.HALF_UP);
                    }
                    return new PositionSnapshot(
                            symbol,
                            instrumentType,
                            holding.getQuantity(),
                            holding.getBuyPrice(),
                            holding.getCurrentPrice(),
                            holding.getCurrentValue(),
                            holding.getProfitLoss(),
                            holding.getProfitLossPercent(),
                            weightPercent,
                            holding.isValuationAvailable()
                    );
                })
                .sorted(Comparator.comparing(
                        position -> position.currentValue() == null ? BigDecimal.ZERO : position.currentValue(),
                        Comparator.reverseOrder()))
                .toList();
    }

    Map<String, BigDecimal> buildInstrumentWeights(List<PositionSnapshot> positions) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.weightPercent() != null)
                .forEach(position -> weights.put(position.symbol(), position.weightPercent()));
        return weights;
    }

    Map<String, BigDecimal> buildTypeWeights(List<PositionSnapshot> positions) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (PositionSnapshot position : positions) {
            if (!position.valuationAvailable() || position.currentValue() == null) {
                continue;
            }
            values.merge(position.instrumentType(), position.currentValue(), BigDecimal::add);
        }

        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }

        Map<String, BigDecimal> percents = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> percents.put(
                        entry.getKey(),
                        entry.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP)
                ));
        return percents;
    }

    private String resolveInstrumentType(String symbol) {
        if ("TRY".equalsIgnoreCase(symbol)) {
            return "CASH_TRY";
        }
        if (symbol.startsWith("TCMB:") || symbol.endsWith("TRY")) {
            return "FX";
        }
        if (symbol.endsWith("USDT") || symbol.matches("^(BTC|ETH|SOL|BNB|XRP|AVAX).*$")) {
            return "CRYPTO";
        }
        if (symbol.length() == 3) {
            return "FUND";
        }
        return "STOCK";
    }

    private String normalizeSymbol(String value) {
        return value == null ? "-" : value.trim().toUpperCase(Locale.ROOT);
    }
}
