package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

/**
 * Builds the LLM prompt for the unified analysis endpoint.
 *
 * Design principles:
 * - No raw numbers in the prompt — only pre-interpreted natural language strings.
 * - Structured insight blocks give the LLM enough context to reason about
 *   technical / fundamental alignment without hallucinating data.
 * - Anti-hallucination rules are stated explicitly at the top.
 */
@Component
public class UnifiedAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(UnifiedAnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Sen Finance Portal'ın kıdemli finansal analistisysen. ");
        sb.append("Aşağıdaki teknik ve temel analiz bloklarını doğal, profesyonel Türkçe finans diliyle sentezleyeceksin.\n\n");

        sb.append("DAVRANIŞ KURALLARI:\n");
        sb.append("- Ham sayı veya yüzde listeleme yapma; yorumlama yap.\n");
        sb.append("- Kesin fiyat hedefi verme.\n");
        sb.append("- 'al', 'sat', 'tut' gibi yatırım tavsiyesi verme.\n");
        sb.append("- 'görünüyor', 'işaret ediyor olabilir', 'mevcut verilere göre' gibi kontrollü dil kullan.\n");
        sb.append("- Sana verilmeyen veri için tahmin yürütme veya uydurma.\n");
        sb.append("- Maksimum 3 highlight ve 2 risk maddesi üret.\n");
        sb.append("- Her madde tek cümle olsun; liste değil yorum yap.\n\n");

        sb.append("SEMBOL: ").append(ctx.symbol()).append("\n\n");

        // ── Technical block ───────────────────────────────────────
        sb.append("=== TEKNİK GÖRÜNÜM ===\n");
        appendIfPresent(sb, "Genel", ctx.technicalSummary());
        appendIfPresent(sb, "Trend", ctx.trendObservation());
        appendIfPresent(sb, "Momentum", ctx.momentumObservation());
        appendIfPresent(sb, "Sinyal", ctx.technicalSignal());
        sb.append("\n");

        // ── Fundamental block (only for STOCK) ───────────────────
        if (ctx.hasFundamentals()) {
            sb.append("=== TEMEL GÖRÜNÜM ===\n");
            appendIfPresent(sb, "Finansal sağlık", ctx.financialHealthLabel());
            appendIfPresent(sb, "Genel", ctx.fundamentalSummary());
            if (!ctx.strengths().isEmpty()) {
                sb.append("Güçlü yanlar: ").append(String.join(" | ", ctx.strengths())).append("\n");
            }
            if (!ctx.weaknesses().isEmpty()) {
                sb.append("Zayıf yanlar: ").append(String.join(" | ", ctx.weaknesses())).append("\n");
            }
            if (!ctx.fundamentalRisks().isEmpty()) {
                sb.append("Riskler: ").append(String.join(" | ", ctx.fundamentalRisks())).append("\n");
            }
            appendIfPresent(sb, "Büyüme", ctx.growthObservation());
            sb.append("\n");
        }

        // ── Alignment reasoning ───────────────────────────────────
        sb.append("=== BÜTÜNLEŞİK YORUM NOTU ===\n");
        sb.append(ctx.conflictNote()).append("\n\n");

        // ── Output format ─────────────────────────────────────────
        sb.append("Yanıtını YALNIZCA şu JSON formatında ver — başka hiçbir şey ekleme:\n");
        sb.append("{\"summary\": \"<2-3 cümle genel değerlendirme>\", ");
        sb.append("\"highlights\": [\"<öne çıkan 1>\", \"<öne çıkan 2>\", \"<öne çıkan 3>\"], ");
        sb.append("\"risks\": [\"<risk 1>\", \"<risk 2>\"]}");

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }
}



