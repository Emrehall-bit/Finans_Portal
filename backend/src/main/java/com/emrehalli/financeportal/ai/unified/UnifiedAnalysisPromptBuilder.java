package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

/**
 * Builds the LLM prompt for the unified analysis endpoint.
 *
 * Design principles:
 * - No raw numbers in the prompt â€” only pre-interpreted natural language strings.
 * - Structured insight blocks give the LLM enough context to reason about
 *   technical / fundamental alignment without hallucinating data.
 * - Anti-hallucination rules are stated explicitly at the top.
 */
@Component
public class UnifiedAnalysisPromptBuilder implements AiPromptBuilder {

    public String build(UnifiedAnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Sen Finance Portal'Ä±n kÄ±demli finansal analistisysen. ");
        sb.append("AÅŸaÄŸÄ±daki teknik ve temel analiz bloklarÄ±nÄ± doÄŸal, profesyonel TÃ¼rkÃ§e finans diliyle sentezleyeceksin.\n\n");

        sb.append("DAVRANIÅ KURALLARI:\n");
        sb.append("- Ham sayÄ± veya yÃ¼zde listeleme yapma; yorumlama yap.\n");
        sb.append("- Kesin fiyat hedefi verme.\n");
        sb.append("- 'al', 'sat', 'tut' gibi yatÄ±rÄ±m tavsiyesi verme.\n");
        sb.append("- 'gÃ¶rÃ¼nÃ¼yor', 'iÅŸaret ediyor olabilir', 'mevcut verilere gÃ¶re' gibi kontrollÃ¼ dil kullan.\n");
        sb.append("- Sana verilmeyen veri iÃ§in tahmin yÃ¼rÃ¼tme veya uydurma.\n");
        sb.append("- Maksimum 3 highlight ve 2 risk maddesi Ã¼ret.\n");
        sb.append("- Her madde tek cÃ¼mle olsun; liste deÄŸil yorum yap.\n\n");

        sb.append("SEMBOL: ").append(ctx.symbol()).append("\n\n");

        // â”€â”€ Technical block â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        sb.append("=== TEKNÄ°K GÃ–RÃœNÃœM ===\n");
        appendIfPresent(sb, "Genel", ctx.technicalSummary());
        appendIfPresent(sb, "Trend", ctx.trendObservation());
        appendIfPresent(sb, "Momentum", ctx.momentumObservation());
        appendIfPresent(sb, "Sinyal", ctx.technicalSignal());
        sb.append("\n");

        // â”€â”€ Fundamental block (only for STOCK) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (ctx.hasFundamentals()) {
            sb.append("=== TEMEL GÃ–RÃœNÃœM ===\n");
            appendIfPresent(sb, "Finansal saÄŸlÄ±k", ctx.financialHealthLabel());
            appendIfPresent(sb, "Genel", ctx.fundamentalSummary());
            if (!ctx.strengths().isEmpty()) {
                sb.append("GÃ¼Ã§lÃ¼ yanlar: ").append(String.join(" | ", ctx.strengths())).append("\n");
            }
            if (!ctx.weaknesses().isEmpty()) {
                sb.append("ZayÄ±f yanlar: ").append(String.join(" | ", ctx.weaknesses())).append("\n");
            }
            if (!ctx.fundamentalRisks().isEmpty()) {
                sb.append("Riskler: ").append(String.join(" | ", ctx.fundamentalRisks())).append("\n");
            }
            appendIfPresent(sb, "BÃ¼yÃ¼me", ctx.growthObservation());
            sb.append("\n");
        }

        // â”€â”€ Alignment reasoning â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        sb.append("=== BÃœTÃœNLEÅÄ°K YORUM NOTU ===\n");
        sb.append(ctx.conflictNote()).append("\n\n");

        // â”€â”€ Output format â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        sb.append("YanÄ±tÄ±nÄ± YALNIZCA ÅŸu JSON formatÄ±nda ver â€” baÅŸka hiÃ§bir ÅŸey ekleme:\n");
        sb.append("{\"summary\": \"<2-3 cÃ¼mle genel deÄŸerlendirme>\", ");
        sb.append("\"highlights\": [\"<Ã¶ne Ã§Ä±kan 1>\", \"<Ã¶ne Ã§Ä±kan 2>\", \"<Ã¶ne Ã§Ä±kan 3>\"], ");
        sb.append("\"risks\": [\"<risk 1>\", \"<risk 2>\"]}");

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }
}




