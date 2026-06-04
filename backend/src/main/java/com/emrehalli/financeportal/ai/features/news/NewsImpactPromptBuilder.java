package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

@Component
public class NewsImpactPromptBuilder {

    public String build(NewsImpactContext ctx, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(AiPromptBuilder.languageInstruction(language));

        sb.append("Sen Turkce finans haberi etki analizi uzmanisisin.\n");
        sb.append("Haberin finansal baglamini ve piyasa uzerindeki olasi etkisini analiz et.\n\n");

        sb.append("DAVRANIS KURALLARI:\n");
        sb.append("1. Haberi tekrar ozetleme - piyasa acisindanki anlami yorumla.\n");
        sb.append("2. Kesin fiyat hareketi veya fiyat hedefi belirtme.\n");
        sb.append("3. Olmayan veri veya istatistik uydurma.\n");
        sb.append("4. Etki net degilse durustce belirt; 'Belirgin kisa vadeli etki saptanamıyor' dogru cevaptir.\n");
        sb.append("5. Orta vadeli yorum icin yeterli veri yoksa 'Orta vadeli etki icin ek gelisme beklenmeli' yaz.\n");
        sb.append("6. Etkilenebilecek varlik net degilse affectedAssets listesini bos birak.\n");
        sb.append("7. Abartili, panikletici veya kesinlik ifade eden dil kullanma.\n\n");

        sb.append("HABER BILGISI:\n");
        sb.append("Baslik: ").append(ctx.title()).append("\n");
        if (hasText(ctx.newsSummary())) {
            sb.append("Icerik: ").append(ctx.newsSummary()).append("\n");
        }
        sb.append("Kategori: ").append(ctx.detectedCategory().name()).append("\n");
        sb.append("Kaynak: ").append(ctx.source()).append("\n");
        if (hasText(ctx.relatedSymbol())) {
            sb.append("Ilgili Sembol: ").append(ctx.relatedSymbol()).append("\n");
        }

        if (!ctx.affectedSectors().isEmpty()) {
            sb.append("\nTESPIT EDILEN SEKTORLER:\n");
            for (String sector : ctx.affectedSectors()) {
                sb.append("  - ").append(sector).append("\n");
            }
        }

        sb.append("\nGOREV:\n");
        sb.append("Asagidaki JSON formatinda yanit ver. Baska hicbir aciklama ekleme.\n");
        sb.append("Emin olmadigin alanlari bos birakabilir veya sinirlilik belirten kisa bir cumle yazabilirsin.\n\n");
        sb.append("{\n");
        sb.append("  \"financialContext\": \"Bu haber piyasa acisindanki onemi? Haberi ozetleme; finansal baglamini acikla.\",\n");
        sb.append("  \"shortTermImpact\": \"24-48 saatlik olasi piyasa tepkisi. Net degilse 'Belirgin kisa vadeli etki saptanamıyor' yaz.\",\n");
        sb.append("  \"mediumTermImpact\": \"Onumuzdeki gunler/haftalar icin olasi etki. Yorum mumkun degilse 'Orta vadeli etki icin ek gelisme beklenmeli' yaz.\",\n");
        sb.append("  \"affectedAssets\": [\"yalnizca makul sekilde iliskilendirilebilen sektor veya sembol\"],\n");
        sb.append("  \"uncertainty\": \"Bu analizin sinirları; haberin etkisini netlestirec henuz bilinmeyen faktorler.\",\n");
        sb.append("  \"highlights\": [\"one cikan finansal cikarim - yalnizca haberden cikarilabiliyorsa\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
