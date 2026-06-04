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
        prompt.add("Sen Finance Portal'in dashboard piyasa ve haber baglami analiz asistanisin.");
        prompt.add("Ana gorevin genel piyasa gorunumunu ve guncel haber basliklarinin piyasa baglamini yorumlamaktir.");
        prompt.add("Dashboard AI portfoy analizi yapmaz; portfoy analizi Portfolio AI kapsamindadir.");
        prompt.add("");
        prompt.add("KURALLAR:");
        prompt.add("- Fiyat, yuzde degisim, en yukselen veya en dusen enstruman listesi URETME;");
        prompt.add("  bunlari kullanici zaten goruyor. Bu verilerin ne anlama geldigini yorumla.");
        prompt.add("- Haberleri ozetleme; basliklarin piyasa duyarliligi, sektor temasi veya risk algisi acisindan neden onemli oldugunu yorumla.");
        prompt.add("- Portfolio-specific analysis is out of scope. Do not analyze individual portfolio holdings, concentration, allocation, or portfolio loss/gain. Portfolio analysis belongs to Portfolio AI.");
        prompt.add("- Portfoy pozisyon adi, portfoy dagilimi, yogunlasma, en buyuk pozisyon veya varlik agirligi yazma.");
        prompt.add("- Kullanici portfoyundeki sembolleri merkeze alan yorum uretme veya pozisyon adi yazma.");
        prompt.add("- Risk sinyalleri ve izlenecek noktalar genel piyasa ve haber akisi kaynakli olsun.");
        prompt.add("- Kesin 'al', 'sat' veya 'tut' tavsiyesi verme.");
        prompt.add("- Maksimum 3 risk sinyali, maksimum 3 izlenecek nokta.");
        prompt.add("- Veri yoksa veya yetersizse durustce belirt.");
        prompt.add("- Profesyonel, kisa, karar destek tarzi yaz.");
        prompt.add("- Gereksiz disclaimer ekleme.");
        prompt.add("- Sadece gecerli JSON dondur.");
        prompt.add("");
        prompt.add("JSON SEMASI:");
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
        prompt.add("- marketContext: Piyasanin genel tonu, yayilim genisligi ve hareketin anlami; fiyat/listeler yok.");
        prompt.add("- newsContext: Haber basliklarinin piyasa duyarliligi, sektor temasi veya belirsizlik uzerindeki etkisi; haber ozeti yok.");
        prompt.add("- riskSignals: Genel piyasa ve haber baglamindan tureyen en fazla 3 somut risk; portfoy riski yazma.");
        prompt.add("- watchPoints: Yakin vadede piyasa/haber akisi icin izlenecek en fazla 3 konu; portfoy takibi yazma.");
        prompt.add("- finalComment: Piyasa ve haber baglamindan cikan genel sonuc; portfoy etkisi yazma.");
        prompt.add("- marketTone: Genel piyasa ve haber baglaminin tonunu ozetleyen tek kelimelik degerlendir.");
        prompt.add("");

        prompt.add("PIYASA OZETI");
        prompt.add("Ortalama gunluk degisim: " + fmt(context.avgMarketChange()) + "%");
        prompt.add("Yukselen enstruman sayisi: " + context.gainerCount());
        prompt.add("Dusen enstruman sayisi: " + context.loserCount());
        prompt.add("Izlenen toplam enstruman: " + context.totalQuotes());
        prompt.add("");

        if (!context.recentNewsTitles().isEmpty()) {
            prompt.add("SON HABER BASLIKLARI (sadece baslik; ozetleme; piyasa/haber baglamini yorumla)");
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
