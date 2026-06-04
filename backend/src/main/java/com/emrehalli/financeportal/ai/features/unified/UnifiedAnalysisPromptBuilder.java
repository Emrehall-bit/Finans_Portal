package com.emrehalli.financeportal.ai.features.unified;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

@Component
public class UnifiedAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(UnifiedAnalysisContext ctx, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(AiPromptBuilder.languageInstruction(language));

        sb.append("Sen Finance Portal'in kidemli finansal analistisysin. ");
        sb.append("Asagidaki teknik ve temel analiz bloklarini dogal, profesyonel finans diliyle sentezleyeceksin.\n\n");

        sb.append("DAVRANIS KURALLARI:\n");
        sb.append("- Ham sayi veya yuzde listeleme yapma; yorumlama yap.\n");
        sb.append("- Kesin fiyat hedefi verme.\n");
        sb.append("- 'al', 'sat', 'tut' gibi yatirim tavsiyesi verme.\n");
        sb.append("- 'gorunuyor', 'isaret ediyor olabilir', 'mevcut verilere gore' gibi kontrollu dil kullan.\n");
        sb.append("- Sana verilmeyen veri icin tahmin yurutme veya uydurma.\n");
        sb.append("- Maksimum 3 highlight ve 2 risk maddesi uret.\n");
        sb.append("- Her madde tek cumle olsun; liste degil yorum yap.\n\n");

        sb.append("SEMBOL: ").append(ctx.symbol()).append("\n\n");

        // ── Technical block ──────────────────────────────────────────────────
        sb.append("=== TEKNIK GORUNUM ===\n");
        appendIfPresent(sb, "Genel", ctx.technicalSummary());
        appendIfPresent(sb, "Trend", ctx.trendObservation());
        appendIfPresent(sb, "Momentum", ctx.momentumObservation());
        appendIfPresent(sb, "Sinyal", ctx.technicalSignal());
        sb.append("\n");

        // ── Fundamental block (only for STOCK) ───────────────────────────────
        if (ctx.hasFundamentals()) {
            sb.append("=== TEMEL GORUNUM ===\n");
            appendIfPresent(sb, "Finansal saglik", ctx.financialHealthLabel());
            appendIfPresent(sb, "Genel", ctx.fundamentalSummary());
            if (!ctx.strengths().isEmpty()) {
                sb.append("Guclu yanlar: ").append(String.join(" | ", ctx.strengths())).append("\n");
            }
            if (!ctx.weaknesses().isEmpty()) {
                sb.append("Zayif yanlar: ").append(String.join(" | ", ctx.weaknesses())).append("\n");
            }
            if (!ctx.fundamentalRisks().isEmpty()) {
                sb.append("Riskler: ").append(String.join(" | ", ctx.fundamentalRisks())).append("\n");
            }
            appendIfPresent(sb, "Buyume", ctx.growthObservation());
            sb.append("\n");
        }

        // ── Alignment reasoning ──────────────────────────────────────────────
        sb.append("=== BUTUNLESIK YORUM NOTU ===\n");
        sb.append(ctx.conflictNote()).append("\n\n");

        // ── Output format ────────────────────────────────────────────────────
        sb.append("Yanitini YALNIZCA su JSON formatinda ver - baska hicbir sey ekleme:\n");
        sb.append("{\"summary\": \"<2-3 cumle genel degerlendirme>\", ");
        sb.append("\"highlights\": [\"<one cikan 1>\", \"<one cikan 2>\", \"<one cikan 3>\"], ");
        sb.append("\"risks\": [\"<risk 1>\", \"<risk 2>\"], ");
        sb.append("\"alignment\": \"<ALIGNED | DIVERGING | TECHNICAL_ONLY>\"}");

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }
}
