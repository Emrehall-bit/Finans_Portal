package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
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
        prompt.add("Sen premium portföy analiz asistanısın.");
        prompt.add("Sadece verilen portföy verisini yorumla.");
        prompt.add("Yatırım tavsiyesi verme; al/sat/tut/kesin yükseliş/kesin düşüş ifadesi kullanma.");
        prompt.add("Portföyü kişisel veri gibi ele al; başka kullanıcı verisi varsayma.");
        prompt.add("Kısa, sade ve profesyonel bir analiz üret.");
        prompt.add("Gereksiz disclaimer ekleme.");
        prompt.add("Sadece geçerli JSON döndür.");
        prompt.add("");

        // ── Çıktı şeması ────────────────────────────────────────────────────
        prompt.add("JSON ŞEMASI:");
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
        prompt.add("- suggestions: Al/sat yönlendirmesi değil; yalnızca risk azaltma, çeşitlendirme,");
        prompt.add("  yoğunlaşma veya vade dengesi perspektifinden yorum yap.");
        prompt.add("  Deterministik notlardaki tespitleri birebir tekrar etme; daha derin yorum üret.");
        prompt.add("- strongestPositions: Sadece kâr/zarar oranı değil;");
        prompt.add("  bu pozisyonun portföy geneline katkısını ve neden öne çıktığını 1 cümleyle açıkla.");
        prompt.add("- weakestPositions: Sadece zarar eden pozisyon değil;");
        prompt.add("  portföy riski açısından neden dikkat gerektirdiğini açıkla.");
        prompt.add("- finalComment: Portföyün tamamı için şu an en kritik TEK konuyu söyle;");
        prompt.add("  diğer alanları tekrar etme.");
        prompt.add("");

        // ── Portföy verisi ───────────────────────────────────────────────────
        prompt.add("PORTFÖY");
        prompt.add("Id: " + context.portfolioId());
        prompt.add("Ad: " + context.portfolioName());
        prompt.add("Toplam değer: " + value(context.totalValue()));
        prompt.add("Toplam kâr/zarar: " + value(context.totalProfitLoss()));
        prompt.add("Toplam kâr/zarar %: " + value(context.totalProfitLossPercent()));
        prompt.add("Pozisyon sayısı: " + context.holdingCount());
        prompt.add("Fiyatlanabilen pozisyon sayısı: " + context.valuedHoldingCount());
        prompt.add("Veri kalitesi: " + context.dataQuality().name());
        prompt.add("");

        prompt.add("TÜR BAZLI AĞIRLIKLAR");
        prompt.add(mapLines(context.typeWeights()));
        prompt.add("");

        prompt.add("ENSTRÜMAN BAZLI AĞIRLIKLAR");
        prompt.add(mapLines(context.instrumentWeights()));
        prompt.add("");

        prompt.add("RİSK SİNYALLERİ");
        prompt.add(listLines(context.riskSignals()));
        prompt.add("");

        prompt.add("DETERMİNİSTİK NOTLAR (tespit edilmiş sorunlar - bunları daha derin yorumla)");
        prompt.add(listLines(context.suggestions()));
        prompt.add("");

        prompt.add("POZİSYONLAR");
        for (PortfolioAnalysisContext.PositionSnapshot position : context.positions()) {
            prompt.add("- " + position.symbol()
                    + " | tip=" + position.instrumentType()
                    + " | adet=" + value(position.quantity())
                    + " | alım=" + value(position.averageBuyPrice())
                    + " | güncel=" + value(position.currentPrice())
                    + " | değer=" + value(position.currentValue())
                    + " | kâr/zarar=" + value(position.profitLoss())
                    + " | kâr/zarar%=" + value(position.profitLossPercent())
                    + " | ağırlık%=" + value(position.weightPercent())
                    + " | fiyatlandı=" + (position.valuationAvailable() ? "EVET" : "HAYIR"));
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
