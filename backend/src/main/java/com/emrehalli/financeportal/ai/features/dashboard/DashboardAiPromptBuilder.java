package com.emrehalli.financeportal.ai.features.dashboard;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.StringJoiner;

@Component
public class DashboardAiPromptBuilder implements AiPromptBuilder {

    public String build(DashboardAiInputContext context, String language) {
        StringJoiner prompt = new StringJoiner("\n");

        prompt.add(AiPromptBuilder.languageInstruction(language).trim());
        prompt.add("");
        prompt.add("Sen Finance Portal'ın dashboard piyasa ve haber bağlamı analiz asistanısın.");
        prompt.add("Ana görevin genel piyasa görünümünü ve güncel haber başlıklarının piyasa bağlamını yorumlamaktır.");
        prompt.add("Dashboard AI portföy analizi yapmaz; portföy analizi Portfolio AI kapsamındadır.");
        prompt.add("");
        prompt.add("KURALLAR:");
        prompt.add("- Fiyat, yüzde değişim, en yükselen veya en düşen enstrüman listesi ÜRETME;");
        prompt.add("  bunları kullanıcı zaten görüyor. Bu verilerin ne anlama geldiğini yorumla.");
        prompt.add("- Haberleri özetleme; başlıkların piyasa duyarlılığı, sektör teması veya risk algısı açısından neden önemli olduğunu yorumla.");
        prompt.add("- Portfolio-specific analysis is out of scope. Do not analyze individual portfolio holdings, concentration, allocation, or portfolio loss/gain. Portfolio analysis belongs to Portfolio AI.");
        prompt.add("- Portföy pozisyon adı, portföy dağılımı, yoğunlaşma, en büyük pozisyon veya varlık ağırlığı yazma.");
        prompt.add("- Kullanıcı portföyündeki sembolleri merkeze alan yorum üretme veya pozisyon adı yazma.");
        prompt.add("- Risk sinyalleri ve izlenecek noktalar genel piyasa ve haber akışı kaynaklı olsun.");
        prompt.add("- Kesin 'al', 'sat' veya 'tut' tavsiyesi verme.");
        prompt.add("- Maksimum 3 risk sinyali, maksimum 3 izlenecek nokta.");
        prompt.add("- Veri yoksa veya yetersizse dürüstçe belirt.");
        prompt.add("- Profesyonel, kısa, karar destek tarzı yaz.");
        prompt.add("- Gereksiz disclaimer ekleme.");
        prompt.add("- Sadece geçerli JSON döndür.");
        prompt.add("");
        prompt.add("JSON ŞEMASI:");
        prompt.add("{");
        prompt.add("  \"marketContext\": \"...\",");
        prompt.add("  \"newsContext\": \"...\",");
        prompt.add("  \"riskSignals\": [\"...\"],");
        prompt.add("  \"watchPoints\": [\"...\"],");
        prompt.add("  \"finalComment\": \"...\",");
        prompt.add("  \"marketTone\": \"POSITIVE|NEUTRAL|CAUTIOUS|NEGATIVE\"");
        prompt.add("}");
        prompt.add("");
        prompt.add("ALAN KURALLARI:");
        prompt.add("- marketContext: Piyasanın genel tonu, yayılım genişliği ve hareketin anlamı; fiyat/listeler yok.");
        prompt.add("- newsContext: Haber başlıklarının piyasa duyarlılığı, sektör teması veya belirsizlik üzerindeki etkisi; haber özeti yok.");
        prompt.add("- riskSignals: Genel piyasa ve haber bağlamından türeyen en fazla 3 somut risk; portföy riski yazma.");
        prompt.add("- watchPoints: Yakın vadede piyasa/haber akışı için izlenecek en fazla 3 konu; portföy takibi yazma.");
        prompt.add("- finalComment: Piyasa ve haber bağlamından çıkan genel sonuç; portföy etkisi yazma.");
        prompt.add("- marketTone: Genel piyasa ve haber bağlamının tonunu özetleyen tek kelimelik değerlendirme.");
        prompt.add("");

        prompt.add("PİYASA ÖZETİ");
        prompt.add("Ortalama günlük değişim: " + fmt(context.avgMarketChange()) + "%");
        prompt.add("Yükselen enstrüman sayısı: " + context.gainerCount());
        prompt.add("Düşen enstrüman sayısı: " + context.loserCount());
        prompt.add("İzlenen toplam enstrüman: " + context.totalQuotes());
        prompt.add("");

        if (!context.recentNewsTitles().isEmpty()) {
            prompt.add("SON HABER BAŞLIKLARI (sadece başlık; özetleme; piyasa/haber bağlamını yorumla)");
            context.recentNewsTitles().stream().limit(5)
                    .forEach(title -> prompt.add("- " + title));
            prompt.add("");
        }

        return prompt.toString();
    }

    private String fmt(BigDecimal val) {
        return val == null ? "-" : val.stripTrailingZeros().toPlainString();
    }
}
