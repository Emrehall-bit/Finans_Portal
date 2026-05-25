package com.emrehalli.financeportal.ai.news;

import org.springframework.stereotype.Component;

@Component
public class NewsImpactPromptBuilder {

    public String build(NewsImpactContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Türkçe finans haberi etki analizi uzmanısın.\n");
        sb.append("Haberin finansal, sektörel ve piyasa etkisini analiz et.\n\n");

        sb.append("DAVRANIŞ KURALLARI:\n");
        sb.append("1. Kesin fiyat hareketi veya fiyat hedefi belirtme\n");
        sb.append("2. Olmayan veri veya istatistik uydurma\n");
        sb.append("3. 'etkileyebilir', 'baskı oluşturabilir', 'destekleyebilir', 'olabilir' gibi ihtimalli dil kullan\n");
        sb.append("4. Clickbait veya abartılı ton kullanma\n");
        sb.append("5. Kısa, anlamlı ve gerçekçi cümleler yaz\n");
        sb.append("6. Mevcut verilere dayanarak yorum yap; spekülatif tahmin sunma\n\n");

        sb.append("HABER BİLGİSİ:\n");
        sb.append("Başlık: ").append(ctx.title()).append("\n");
        if (hasText(ctx.newsSummary())) {
            sb.append("Özet: ").append(ctx.newsSummary()).append("\n");
        }
        sb.append("Kategori: ").append(ctx.detectedCategory().name()).append("\n");
        sb.append("Kaynak: ").append(ctx.source()).append("\n");
        if (hasText(ctx.relatedSymbol())) {
            sb.append("İlgili Sembol: ").append(ctx.relatedSymbol()).append("\n");
        }

        sb.append("\nÖN ANALİZ:\n");
        if (!ctx.affectedSectors().isEmpty()) {
            sb.append("Tespit Edilen Sektör Etkileri:\n");
            for (String sector : ctx.affectedSectors()) {
                sb.append("  - ").append(sector).append("\n");
            }
        }
        sb.append("Başlangıç Değerlendirmesi: ").append(ctx.initialSentiment())
          .append(", Risk Seviyesi: ").append(ctx.initialRiskLevel()).append("\n\n");

        sb.append("TEKNİK GÖREV:\n");
        sb.append("Aşağıdaki JSON formatında yanıt ver. Başka hiçbir açıklama ekleme:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"Haberin kısa ve tarafsız finansal özeti (1-2 cümle, ihtimalli dil kullan)\",\n");
        sb.append("  \"marketImpact\": \"Genel piyasaya olası etkisi (1-2 cümle, kesin konuşma)\",\n");
        sb.append("  \"highlights\": [\"öne çıkan finansal nokta 1\", \"öne çıkan finansal nokta 2\", \"öne çıkan finansal nokta 3\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}



