package com.emrehalli.financeportal.ai.portfolio;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class PortfolioAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(PortfolioAnalysisContext context, String language) {
        StringJoiner prompt = new StringJoiner("\n");

        // ── Rol ve kural bloğu ──────────────────────────────────────────────
        prompt.add(AiPromptBuilder.languageInstruction(language).trim());
        prompt.add("");
        prompt.add("Sen premium portfoy analiz asistanisin.");
        prompt.add("Sadece verilen portfoy verisini yorumla.");
        prompt.add("Yatirim tavsiyesi verme; al/sat/tut/kesin yukselis/kesin dusis ifadesi kullanma.");
        prompt.add("Portfoyu kisisel veri gibi ele al; baska kullanici verisi varsayma.");
        prompt.add("Kisa, sade ve profesyonel bir analiz uret.");
        prompt.add("Gereksiz disclaimer ekleme.");
        prompt.add("Sadece gecerli JSON dondur.");
        prompt.add("");

        // ── Çıktı şeması ────────────────────────────────────────────────────
        prompt.add("JSON SEMASI:");
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

        // ── Alan kuralları ───────────────────────────────────────────────────
        prompt.add("ALAN KURALLARI:");
        prompt.add("- suggestions: Al/sat yonlendirmesi degil; yalnizca risk azaltma, cesitlendirme,");
        prompt.add("  yogunlasma veya vade dengesi perspektifinden yorum yap.");
        prompt.add("  Deterministik notlardaki tespitleri birebir tekrar etme; daha derin yorum uret.");
        prompt.add("- strongestPositions: Sadece kar/zarar orani degil;");
        prompt.add("  bu pozisyonun portfoy geneline katkisini ve neden one ciktigini 1 cumleyle acikla.");
        prompt.add("- weakestPositions: Sadece zarar eden pozisyon degil;");
        prompt.add("  portfoy riski acisindan neden dikkat gerektirdigini acikla.");
        prompt.add("- finalComment: Portfoyun tamami icin su an en kritik TEK konuyu soyle;");
        prompt.add("  diger alanlari tekrar etme.");
        prompt.add("");

        // ── Portföy verisi ───────────────────────────────────────────────────
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

        prompt.add("DETERMINISTIK NOTLAR (tespit edilmis sorunlar - bunlari daha derin yorumla)");
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
        if (values == null || values.isEmpty()) return "-";
        StringJoiner joiner = new StringJoiner("\n");
        values.forEach((key, val) -> joiner.add(key + ": " + value(val)));
        return joiner.toString();
    }

    private String listLines(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "-";
        StringJoiner joiner = new StringJoiner("\n");
        values.forEach(item -> joiner.add("- " + item));
        return joiner.toString();
    }

    private String value(BigDecimal val) {
        return val == null ? "-" : val.stripTrailingZeros().toPlainString();
    }
}
