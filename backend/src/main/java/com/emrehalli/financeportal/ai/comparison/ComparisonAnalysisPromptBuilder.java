package com.emrehalli.financeportal.ai.comparison;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
public class ComparisonAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(ComparisonAnalysisContext context) {
        ComparisonAnalysisContext.InstrumentSnapshot left = context.left();
        ComparisonAnalysisContext.InstrumentSnapshot right = context.right();

        StringJoiner prompt = new StringJoiner("\n");
        prompt.add("Sen finansal veri uydurmayan premium bir karsilastirma asistansın.");
        prompt.add("Sadece verilen deterministic verilere dayan.");
        prompt.add("Veri olmayan alanlarda acikca veri sinirli de.");
        prompt.add("Yatirim tavsiyesi verme.");
        prompt.add("Kesin al/sat/tut, hedef fiyat, garanti getiri veya kesinlik ifadesi kullanma.");
        prompt.add("Gereksiz uzun disclaimer yazma.");
        prompt.add("Grafik karsilastirma sonucuyla celisecek fiyat uydurma veya performans iddiasi ekleme.");
        prompt.add("Cevabi sadece gecerli JSON olarak don.");
        prompt.add("JSON semasi:");
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
        prompt.add("Teknik karsilastirma kisa vadeli gorunume odaklansin.");
        prompt.add("Temel karsilastirma sadece mevcut temel veri varsa yapilsin.");
        prompt.add("Risk karsilastirmasi dengeli ve ihtiyatli olsun.");
        prompt.add("Ozetler premium kalite hissi versin ama abartili olmasin.");
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
        block.add(title + " ENSTRUMAN");
        block.add("Sembol: " + snapshot.symbol());
        block.add("Gorunen ad: " + snapshot.displayName());
        block.add("Teknik veri var mi: " + yesNo(snapshot.technicalAvailable()));
        block.add("Son fiyat: " + value(snapshot.latestPrice()));
        block.add("RSI14: " + value(snapshot.rsi()));
        block.add("Trend: " + snapshot.trendLabel());
        block.add("Teknik sinyal: " + snapshot.technicalSignal().name());
        block.add("Teknik risk: " + snapshot.technicalRisk().name());
        block.add("Teknik guclu yonler: " + list(snapshot.technicalStrengths()));
        block.add("Teknik zayif yonler: " + list(snapshot.technicalWeaknesses()));
        block.add("Temel veri var mi: " + yesNo(snapshot.fundamentalsAvailable()));
        block.add("Finansal saglik: " + snapshot.financialHealth().name());
        block.add("Temel guclu yonler: " + list(snapshot.fundamentalStrengths()));
        block.add("Temel zayif yonler: " + list(snapshot.fundamentalWeaknesses()));
        block.add("Temel riskler: " + list(snapshot.fundamentalRisks()));
        block.add("Genel risk skoru: " + snapshot.overallRiskScore());
        return block.toString();
    }

    private String yesNo(boolean value) {
        return value ? "EVET" : "HAYIR";
    }

    private String value(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String list(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "-" : String.join(" | ", values);
    }
}
