package com.emrehalli.financeportal.ai.features.comparison;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
public class ComparisonAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(ComparisonAnalysisContext context, String language) {
        ComparisonAnalysisContext.InstrumentSnapshot left = context.left();
        ComparisonAnalysisContext.InstrumentSnapshot right = context.right();

        StringJoiner prompt = new StringJoiner("\n");
        prompt.add(AiPromptBuilder.languageInstruction(language).trim());
        prompt.add("");
        prompt.add("Sen finansal veri uydurmayan premium bir karşılaştırma asistanısın.");
        prompt.add("Sadece verilen deterministik verilere dayan.");
        prompt.add("Veri olmayan alanlarda açıkça veri sınırlı de.");
        prompt.add("Yatırım tavsiyesi verme.");
        prompt.add("Kesin al/sat/tut, hedef fiyat, garanti getiri veya kesinlik ifadesi kullanma.");
        prompt.add("Gereksiz uzun disclaimer yazma.");
        prompt.add("Grafik karşılaştırma sonucuyla çelişecek fiyat uydurma veya performans iddiası ekleme.");
        prompt.add("Cevabı sadece geçerli JSON olarak döndür.");
        prompt.add("JSON şeması:");
        prompt.add("{");
        prompt.add("  \"summary\": \"...\",");
        prompt.add("  \"technicalComparison\": \"...\",");
        prompt.add("  \"fundamentalComparison\": \"...\",");
        prompt.add("  \"riskComparison\": \"...\",");
        prompt.add("  \"strengthsLeft\": [\"...\"],");
        prompt.add("  \"strengthsRight\": [\"...\"],");
        prompt.add("  \"weaknessesLeft\": [\"...\"],");
        prompt.add("  \"weaknessesRight\": [\"...\"],");
        prompt.add("  \"finalComment\": \"...\"");
        prompt.add("}");
        prompt.add("");
        prompt.add("Teknik karşılaştırma kısa vadeli görünüme odaklanmış olsun.");
        prompt.add("Temel karşılaştırma sadece mevcut temel veri varsa yapılsın.");
        prompt.add("Risk karşılaştırması dengeli ve ihtiyatlı olsun.");
        prompt.add("Özetler premium kalite hissi versin ama abartılı olmasın.");
        prompt.add("");
        prompt.add("VERI KALITESI: " + context.dataQuality().name());
        prompt.add("");
        prompt.add(buildInstrumentBlock("SOL", left));
        prompt.add("");
        prompt.add(buildInstrumentBlock("SAG", right));
        return prompt.toString();
    }

    private String buildInstrumentBlock(String title, ComparisonAnalysisContext.InstrumentSnapshot snapshot) {
        StringJoiner block = new StringJoiner("\n");
        block.add(title + " ENSTRÜMAN");
        block.add("Sembol: " + snapshot.symbol());
        block.add("Görünen ad: " + snapshot.displayName());
        block.add("Teknik veri var mı: " + yesNo(snapshot.technicalAvailable()));
        block.add("Son fiyat: " + value(snapshot.latestPrice()));
        block.add("RSI14: " + value(snapshot.rsi()));
        block.add("Trend: " + snapshot.trendLabel());
        block.add("Teknik sinyal: " + snapshot.technicalSignal().name());
        block.add("Teknik risk: " + snapshot.technicalRisk().name());
        block.add("Teknik güçlü yönler: " + list(snapshot.technicalStrengths()));
        block.add("Teknik zayıf yönler: " + list(snapshot.technicalWeaknesses()));
        block.add("Temel veri var mı: " + yesNo(snapshot.fundamentalsAvailable()));
        block.add("Finansal sağlık: " + snapshot.financialHealth().name());
        block.add("Temel güçlü yönler: " + list(snapshot.fundamentalStrengths()));
        block.add("Temel zayıf yönler: " + list(snapshot.fundamentalWeaknesses()));
        block.add("Temel riskler: " + list(snapshot.fundamentalRisks()));
        block.add("Genel risk skoru: " + snapshot.overallRiskScore());
        return block.toString();
    }

    private String yesNo(boolean value) { return value ? "EVET" : "HAYIR"; }
    private String value(Object value)   { return value == null ? "-" : String.valueOf(value); }
    private String list(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "-" : String.join(" | ", values);
    }
}
