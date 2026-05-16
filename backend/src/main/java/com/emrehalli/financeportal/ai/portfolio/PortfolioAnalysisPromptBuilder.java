package com.emrehalli.financeportal.ai.portfolio;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class PortfolioAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(PortfolioAnalysisContext context) {
        StringJoiner prompt = new StringJoiner("\n");
        prompt.add("Sen premium portfoy analiz asistansın.");
        prompt.add("Sadece verilen deterministic portfoy verisini yorumla.");
        prompt.add("Yatirim tavsiyesi verme.");
        prompt.add("Al/sat onerisi, kesin ifade, hedef fiyat veya uydurma veri kullanma.");
        prompt.add("Portfoyu kisisel veri gibi ele al; baska kullanici verisi varsayma.");
        prompt.add("Kisa, sade Turkce ve premium hissi veren bir yorum uret.");
        prompt.add("Gereksiz disclaimer ekleme.");
        prompt.add("Sadece gecerli JSON dondur.");
        prompt.add("{");
        prompt.add("  \"summary\": \"...\",");
        prompt.add("  \"allocationComment\": \"...\",");
        prompt.add("  \"riskComment\": \"...\",");
        prompt.add("  \"diversificationComment\": \"...\",");
        prompt.add("  \"strongestPositions\": [\"...\"],");
        prompt.add("  \"weakestPositions\": [\"...\"],");
        prompt.add("  \"riskSignals\": [\"...\"],");
        prompt.add("  \"suggestions\": [\"...\"],");
        prompt.add("  \"finalComment\": \"...\"");
        prompt.add("}");
        prompt.add("");
        prompt.add("PORTFOY");
        prompt.add("Id: " + context.portfolioId());
        prompt.add("Ad: " + context.portfolioName());
        prompt.add("Toplam deger: " + value(context.totalValue()));
        prompt.add("Toplam kar/zarar: " + value(context.totalProfitLoss()));
        prompt.add("Toplam kar/zarar %: " + value(context.totalProfitLossPercent()));
        prompt.add("Pozisyon sayisi: " + context.holdingCount());
        prompt.add("Fiyatlanabilen pozisyon sayisi: " + context.valuedHoldingCount());
        prompt.add("Veri kalitesi: " + context.dataQuality().name());
        prompt.add("");
        prompt.add("TUR BAZLI AGIRLIKLAR");
        prompt.add(mapLines(context.typeWeights()));
        prompt.add("");
        prompt.add("ENSTRUMAN BAZLI AGIRLIKLAR");
        prompt.add(mapLines(context.instrumentWeights()));
        prompt.add("");
        prompt.add("RISK SINYALLERI");
        prompt.add(listLines(context.riskSignals()));
        prompt.add("");
        prompt.add("DETERMINISTIC NOTLAR");
        prompt.add(listLines(context.suggestions()));
        prompt.add("");
        prompt.add("POZISYONLAR");
        for (PortfolioAnalysisContext.PositionSnapshot position : context.positions()) {
            prompt.add("- " + position.symbol()
                    + " | tip=" + position.instrumentType()
                    + " | adet=" + value(position.quantity())
                    + " | alim=" + value(position.averageBuyPrice())
                    + " | guncel=" + value(position.currentPrice())
                    + " | deger=" + value(position.currentValue())
                    + " | karzarar=" + value(position.profitLoss())
                    + " | karzarar%=" + value(position.profitLossPercent())
                    + " | agirlik%=" + value(position.weightPercent())
                    + " | fiyatlandi=" + (position.valuationAvailable() ? "EVET" : "HAYIR"));
        }
        return prompt.toString();
    }

    private String mapLines(Map<String, BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner("\n");
        values.forEach((key, value) -> joiner.add(key + ": " + value(value)));
        return joiner.toString();
    }

    private String listLines(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner("\n");
        values.forEach(item -> joiner.add("- " + item));
        return joiner.toString();
    }

    private String value(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
