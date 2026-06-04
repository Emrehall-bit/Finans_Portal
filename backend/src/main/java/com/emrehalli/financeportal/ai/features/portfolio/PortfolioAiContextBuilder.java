package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisContext.PositionSnapshot;
import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class PortfolioAiContextBuilder {

    private final PortfolioWeightCalculator weightCalculator;

    PortfolioAiContextBuilder(PortfolioWeightCalculator weightCalculator) {
        this.weightCalculator = weightCalculator;
    }

    PortfolioAnalysisContext build(Portfolio portfolio,
                                   List<PortfolioHoldingDto> holdings,
                                   PortfolioSummaryResponse summary) {
        BigDecimal totalValue = summary.getCurrentValue();
        int valuedHoldingCount = (int) holdings.stream().filter(PortfolioHoldingDto::isValuationAvailable).count();
        DataQuality dataQuality = determineDataQuality(holdings.size(), valuedHoldingCount, summary);
        List<PositionSnapshot> positions = weightCalculator.buildPositions(holdings, totalValue);
        Map<String, BigDecimal> instrumentWeights = weightCalculator.buildInstrumentWeights(positions);
        Map<String, BigDecimal> typeWeights = weightCalculator.buildTypeWeights(positions);
        List<String> riskSignals = buildRiskSignals(positions, typeWeights, summary);
        List<String> suggestions = buildDeterministicSuggestions(positions, typeWeights, summary, dataQuality);

        return new PortfolioAnalysisContext(
                portfolio.getId(),
                portfolio.getPortfolioName(),
                totalValue,
                summary.getProfitLoss(),
                summary.getProfitLossPercent(),
                holdings.size(),
                valuedHoldingCount,
                positions,
                instrumentWeights,
                typeWeights,
                riskSignals,
                suggestions,
                dataQuality
        );
    }

    private List<String> buildRiskSignals(List<PositionSnapshot> positions,
                                          Map<String, BigDecimal> typeWeights,
                                          PortfolioSummaryResponse summary) {
        List<String> signals = new ArrayList<>();

        positions.stream()
                .filter(position -> position.weightPercent() != null)
                .max(Comparator.comparing(PositionSnapshot::weightPercent))
                .ifPresent(largest -> {
                    if (largest.weightPercent().compareTo(BigDecimal.valueOf(60)) > 0) {
                        signals.add(largest.symbol() + " tek basina portfoyun %60'inden fazlasini tasiyor; yogunlasma riski yuksek.");
                    } else if (largest.weightPercent().compareTo(BigDecimal.valueOf(40)) > 0) {
                        signals.add(largest.symbol() + " portfoyde baskin agirlikta; yogunlasma dikkat gerektiriyor.");
                    }
                });

        if (positions.size() <= 2 && !positions.isEmpty()) {
            signals.add("Portfoy 1-2 varlikta toplaniyor; cesitlilik dusuk.");
        }

        if (summary.getProfitLossPercent() != null && summary.getProfitLossPercent().compareTo(BigDecimal.valueOf(-15)) <= 0) {
            signals.add("Toplam zarar oranÄ± belirgin seviyede negatif; portfoy baski altinda olabilir.");
        }

        long unavailableCount = positions.stream().filter(position -> !position.valuationAvailable()).count();
        if (unavailableCount > 0) {
            signals.add(unavailableCount + " pozisyon icin canli veya saglam fiyatlama bulunamadi.");
        }

        if (!typeWeights.containsKey("CASH_TRY")) {
            signals.add("Portfoyde TRY nakit tamponu gorunmuyor; tum risk varliklar uzerinden okunuyor olabilir.");
        }

        if (signals.isEmpty()) {
            signals.add("Belirgin bir yapisal risk sinyali cikmiyor; portfoy dagilimi genel olarak dengeli gorunuyor.");
        }
        return List.copyOf(signals);
    }

    private List<String> buildDeterministicSuggestions(List<PositionSnapshot> positions,
                                                       Map<String, BigDecimal> typeWeights,
                                                       PortfolioSummaryResponse summary,
                                                       DataQuality dataQuality) {
        List<String> suggestions = new ArrayList<>();

        if (positions.size() <= 2 && !positions.isEmpty()) {
            suggestions.add("Cesitlilik seviyesi dusuk oldugu icin portfoy yorumu birkac pozisyona asiri duyarlidir.");
        }
        if (typeWeights.size() <= 1 && !positions.isEmpty()) {
            suggestions.add("Tur bazli dagilim sinirli; portfoy tek tema etrafinda donuyor olabilir.");
        }
        if (summary.getMissingPriceCount() > 0 || dataQuality != DataQuality.COMPLETE) {
            suggestions.add("Veri kalitesi sinirli alanlarda yorum ihtiyatla okunmali.");
        }
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .max(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .ifPresent(best -> suggestions.add(best.symbol() + " portfoyde gorece en guclu performans veren pozisyon olarak one cikiyor."));
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .min(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .ifPresent(worst -> suggestions.add(worst.symbol() + " portfoyde en zayif performans veren pozisyon olarak izlenmeli."));

        if (suggestions.isEmpty()) {
            suggestions.add("Portfoy yorumu icin kullanilabilir veri sinirli ama mevcut dagilim temel bir cati sunuyor.");
        }
        return List.copyOf(suggestions);
    }

    private DataQuality determineDataQuality(int holdingCount, int valuedHoldingCount, PortfolioSummaryResponse summary) {
        if (holdingCount == 0) {
            return DataQuality.LIMITED;
        }
        if (valuedHoldingCount == holdingCount && summary.getCurrentValue() != null) {
            return DataQuality.COMPLETE;
        }
        if (valuedHoldingCount > 0) {
            return DataQuality.PARTIAL;
        }
        return DataQuality.LIMITED;
    }
}
