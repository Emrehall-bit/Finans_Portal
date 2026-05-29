package com.emrehalli.financeportal.ai.news;

import org.springframework.stereotype.Component;

@Component
public class NewsImpactPromptBuilder {

    public String build(NewsImpactContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("TÃ¼rkÃ§e finans haberi etki analizi uzmanÄ±sÄ±n.\n");
        sb.append("Haberin finansal, sektÃ¶rel ve piyasa etkisini analiz et.\n\n");

        sb.append("DAVRANIÅ KURALLARI:\n");
        sb.append("1. Kesin fiyat hareketi veya fiyat hedefi belirtme\n");
        sb.append("2. Olmayan veri veya istatistik uydurma\n");
        sb.append("3. 'etkileyebilir', 'baskÄ± oluÅŸturabilir', 'destekleyebilir', 'olabilir' gibi ihtimalli dil kullan\n");
        sb.append("4. Clickbait veya abartÄ±lÄ± ton kullanma\n");
        sb.append("5. KÄ±sa, anlamlÄ± ve gerÃ§ekÃ§i cÃ¼mleler yaz\n");
        sb.append("6. Mevcut verilere dayanarak yorum yap; spekÃ¼latif tahmin sunma\n\n");

        sb.append("HABER BÄ°LGÄ°SÄ°:\n");
        sb.append("BaÅŸlÄ±k: ").append(ctx.title()).append("\n");
        if (hasText(ctx.newsSummary())) {
            sb.append("Ã–zet: ").append(ctx.newsSummary()).append("\n");
        }
        sb.append("Kategori: ").append(ctx.detectedCategory().name()).append("\n");
        sb.append("Kaynak: ").append(ctx.source()).append("\n");
        if (hasText(ctx.relatedSymbol())) {
            sb.append("Ä°lgili Sembol: ").append(ctx.relatedSymbol()).append("\n");
        }

        sb.append("\nÃ–N ANALÄ°Z:\n");
        if (!ctx.affectedSectors().isEmpty()) {
            sb.append("Tespit Edilen SektÃ¶r Etkileri:\n");
            for (String sector : ctx.affectedSectors()) {
                sb.append("  - ").append(sector).append("\n");
            }
        }
        sb.append("BaÅŸlangÄ±Ã§ DeÄŸerlendirmesi: ").append(ctx.initialSentiment())
          .append(", Risk Seviyesi: ").append(ctx.initialRiskLevel()).append("\n\n");

        sb.append("TEKNÄ°K GÃ–REV:\n");
        sb.append("AÅŸaÄŸÄ±daki JSON formatÄ±nda yanÄ±t ver. BaÅŸka hiÃ§bir aÃ§Ä±klama ekleme:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"Haberin kÄ±sa ve tarafsÄ±z finansal Ã¶zeti (1-2 cÃ¼mle, ihtimalli dil kullan)\",\n");
        sb.append("  \"marketImpact\": \"Genel piyasaya olasÄ± etkisi (1-2 cÃ¼mle, kesin konuÅŸma)\",\n");
        sb.append("  \"highlights\": [\"Ã¶ne Ã§Ä±kan finansal nokta 1\", \"Ã¶ne Ã§Ä±kan finansal nokta 2\", \"Ã¶ne Ã§Ä±kan finansal nokta 3\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}




