package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.features.portfolio.PortfolioAnalysisContext.PositionSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class PortfolioFallbackBuilder {

    PortfolioAnalysisResponse deterministicFallback(PortfolioAnalysisContext context) {
        List<String> strongestPositions = context.positions().stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .sorted(Comparator.comparing(PositionSnapshot::profitLossPercent).reversed())
                .limit(3)
                .map(position -> position.symbol() + " (%" + format(position.profitLossPercent()) + ")")
                .toList();

        List<String> weakestPositions = context.positions().stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .sorted(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .limit(3)
                .map(position -> position.symbol() + " (%" + format(position.profitLossPercent()) + ")")
                .toList();

        return new PortfolioAnalysisResponse(
                context.portfolioId(),
                context.portfolioName(),
                context.totalValue(),
                context.totalProfitLoss(),
                context.totalProfitLossPercent(),
                buildSummary(context),
                buildAllocationComment(context),
                buildRiskComment(context),
                buildDiversificationComment(context),
                strongestPositions,
                weakestPositions,
                context.riskSignals(),
                context.suggestions(),
                buildFinalComment(context),
                context.dataQuality(),
                null,
                false,
                AiResponseMetadata.deterministic(context.dataQuality().name())
        );
    }

    PortfolioAnalysisResponse withCacheHit(PortfolioAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic(response.dataQuality().name()).withCacheHit(cacheHit);
        return new PortfolioAnalysisResponse(
                response.portfolioId(),
                response.portfolioName(),
                response.totalValue(),
                response.totalProfitLoss(),
                response.totalProfitLossPercent(),
                response.summary(),
                response.allocationComment(),
                response.riskComment(),
                response.diversificationComment(),
                response.strongestPositions(),
                response.weakestPositions(),
                response.riskSignals(),
                response.suggestions(),
                response.finalComment(),
                response.dataQuality(),
                response.providerUsed(),
                response.fallbackUsed(),
                metadata
        );
    }

    private String buildSummary(PortfolioAnalysisContext context) {
        if (context.holdingCount() == 0) {
            return "Portfoy bos oldugu icin AI yorumu temel seviye bilgiyle sinirli. Yeni pozisyonlar eklendiginde dagilim ve risk resmi daha anlamli okunur.";
        }
        return context.portfolioName() + " portfoyu " + context.holdingCount() + " pozisyondan olusuyor. Toplam deger "
                + formatMoney(context.totalValue()) + ", toplam performans ise "
                + formatMoney(context.totalProfitLoss()) + " ve %" + format(context.totalProfitLossPercent()) + " seviyesinde.";
    }

    private String buildAllocationComment(PortfolioAnalysisContext context) {
        if (context.typeWeights().isEmpty()) {
            return "Tur bazli dagilim hesaplanamadi; fiyatlanabilir portfoy verisi sinirli.";
        }
        Map.Entry<String, BigDecimal> dominant = context.typeWeights().entrySet().iterator().next();
        return "Portfoy dagiliminda " + dominant.getKey() + " grubu %" + format(dominant.getValue())
                + " ile en yuksek payi tasiyor. Enstruman bazli agirliklar yogunlasma riskini belirleyen ana unsur.";
    }

    private String buildRiskComment(PortfolioAnalysisContext context) {
        return context.riskSignals().isEmpty()
                ? "Belirgin ek risk sinyali uretilemedi."
                : context.riskSignals().getFirst();
    }

    private String buildDiversificationComment(PortfolioAnalysisContext context) {
        if (context.holdingCount() == 0) {
            return "Cesitlilik degerlendirmesi icin aktif pozisyon yok.";
        }
        if (context.holdingCount() <= 2) {
            return "Pozisyon sayisi dusuk oldugu icin cesitlilik sinirli ve tekil hareketlere duyarlilik yuksek.";
        }
        if (context.typeWeights().size() <= 1) {
            return "Portfoy farkli arac turlerine yayilmadigi icin tematik cesitlilik zayif.";
        }
        return "Pozisyon ve tur dagilimi belirli bir cesitlilik sagliyor; yine de baskin agirliklar yakindan izlenmeli.";
    }

    private String buildFinalComment(PortfolioAnalysisContext context) {
        return "Portfoy yorumu mevcut holdings dagilimi ve fiyatlanabilen pozisyonlar uzerinden uretildi. En kritik sinyal, "
                + context.riskSignals().getFirst().toLowerCase(Locale.ROOT)
                + " Veriler guncellendikce yorumun agirlik merkezi degisebilir.";
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
