package com.emrehalli.financeportal.ai.news;

import org.springframework.stereotype.Component;

@Component
public class NewsImpactPromptBuilder {

    public String build(NewsImpactContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Sen Türkçe finans haberi etki analizi uzmanısın.\n");
        sb.append("Haberin finansal bağlamını ve piyasa üzerindeki olası etkisini analiz et.\n\n");

        sb.append("DAVRANIŞ KURALLARI:\n");
        sb.append("1. Haberi tekrar özetleme — piyasa açısından ne anlama geldiğini yorumla.\n");
        sb.append("2. Kesin fiyat hareketi veya fiyat hedefi belirtme.\n");
        sb.append("3. Olmayan veri veya istatistik uydurma.\n");
        sb.append("4. Etki net değilse dürüstçe belirt; 'Belirgin kısa vadeli etki saptanamıyor' yazmak doğru cevaptır.\n");
        sb.append("5. Orta vadeli yorum için yeterli veri yoksa 'Orta vadeli etkiyi değerlendirmek için ek gelişme beklenmeli' yaz.\n");
        sb.append("6. Etkilenebilecek varlık net değilse affectedAssets listesini boş bırak.\n");
        sb.append("7. Abartılı, panikletici veya kesinlik ifade eden dil kullanma.\n\n");

        sb.append("HABER BİLGİSİ:\n");
        sb.append("Başlık: ").append(ctx.title()).append("\n");
        if (hasText(ctx.newsSummary())) {
            sb.append("İçerik: ").append(ctx.newsSummary()).append("\n");
        }
        sb.append("Kategori: ").append(ctx.detectedCategory().name()).append("\n");
        sb.append("Kaynak: ").append(ctx.source()).append("\n");
        if (hasText(ctx.relatedSymbol())) {
            sb.append("İlgili Sembol: ").append(ctx.relatedSymbol()).append("\n");
        }

        if (!ctx.affectedSectors().isEmpty()) {
            sb.append("\nTESPİT EDİLEN SEKTÖRLER:\n");
            for (String sector : ctx.affectedSectors()) {
                sb.append("  - ").append(sector).append("\n");
            }
        }

        sb.append("\nGÖREV:\n");
        sb.append("Aşağıdaki JSON formatında yanıt ver. Başka hiçbir açıklama ekleme.\n");
        sb.append("Emin olmadığın alanları boş bırakabilir veya sınırlılığı belirten kısa bir cümle yazabilirsin.\n\n");
        sb.append("{\n");
        sb.append("  \"financialContext\": \"Bu haber piyasa açısından neden önemli? Haberi özetleme; finansal bağlamını açıkla.\",\n");
        sb.append("  \"shortTermImpact\": \"24-48 saatlik olası piyasa tepkisi. Net değilse 'Belirgin kısa vadeli etki saptanamıyor' yaz.\",\n");
        sb.append("  \"mediumTermImpact\": \"Önümüzdeki günler/haftalar için olası etki. Yorum mümkün değilse 'Orta vadeli etki için ek gelişme beklenmeli' yaz.\",\n");
        sb.append("  \"affectedAssets\": [\"yalnızca makul şekilde ilişkilendirilebilen sektör veya sembol\"],\n");
        sb.append("  \"uncertainty\": \"Bu analizin sınırları; haberin etkisini netleştirecek bilinmeyen faktörler.\",\n");
        sb.append("  \"highlights\": [\"öne çıkan finansal çıkarım — yalnızca haberden çıkarılabiliyorsa\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
