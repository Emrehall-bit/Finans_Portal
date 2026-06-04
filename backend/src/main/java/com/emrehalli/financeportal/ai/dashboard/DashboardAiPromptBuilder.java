package com.emrehalli.financeportal.ai.dashboard;

import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
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
        prompt.add("Portfoy verisi varsa sadece ikincil ve kisa baglam olarak kullan; detayli portfoy analizi yapma.");
        prompt.add("");
        prompt.add("KURALLAR:");
        prompt.add("- Fiyat, yuzde degisim, en yukselen veya en dusen enstruman listesi URETME;");
        prompt.add("  bunlari kullanici zaten goruyor. Bu verilerin ne anlama geldigini yorumla.");
        prompt.add("- Haberleri ozetleme; basliklarin piyasa duyarliligi, sektor temasi veya risk algisi acisindan ne anlattigini yorumla.");
        prompt.add("- Portfoy odakli uzun performans, agirlik, kar/zarar veya pozisyon analizi yapma.");
        prompt.add("- portfolioImpact sadece portfoy varsa ve en fazla 1-2 kisa cumle olacak; portfoy yoksa null veya bos string dondur.");
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
        prompt.add("  \"portfolioImpact\": \"...\",");
        prompt.add("  \"riskSignals\": [\"...\"],");
        prompt.add("  \"watchPoints\": [\"...\"],");
        prompt.add("  \"finalComment\": \"...\",");
        prompt.add("  \"marketTone\": \"POSITIVE|NEUTRAL|CAUTIOUS|NEGATIVE\"");
        prompt.add("}");
        prompt.add("");
        prompt.add("ALAN KURALLARI:");
        prompt.add("- marketContext: Piyasanin genel tonu, yayilim genisligi ve hareketin anlami; fiyat/listeler yok.");
        prompt.add("- newsContext: Haber basliklarinin piyasa duyarliligi, sektor temasi veya belirsizlik uzerindeki etkisi; haber ozeti yok.");
        prompt.add("- riskSignals: Oncelikle piyasa ve haber baglamindan tureyen en fazla 3 somut risk; portfoy detayina gomulme.");
        prompt.add("- watchPoints: Yakin vadede piyasa/haber akisi icin izlenecek en fazla 3 konu; belirli ve pratik olsun.");
        prompt.add("- portfolioImpact: Sadece portfoy varsa kisa ikincil etki; yoksa null veya bos string.");
        prompt.add("- finalComment: Piyasa ve haber baglamindan cikan genel sonuc; portfoy alanini tekrarlama.");
        prompt.add("- marketTone: Genel piyasa ve haber baglaminin tonunu ozetleyen tek kelimelik degerlendir.");
        prompt.add("");

        if (context.hasPortfolio()) {
            prompt.add("PORTFOY OZETI (ikincil baglam; detayli analiz yapma)");
            prompt.add("Toplam deger: " + fmt(context.totalValue()));
            prompt.add("Gunluk kar/zarar: " + fmt(context.dailyProfitLoss()));
            prompt.add("Toplam kar/zarar: " + fmt(context.totalProfitLoss()));
            prompt.add("Pozisyon sayisi: " + context.holdingCount());
            prompt.add("");

            if (!context.topHoldings().isEmpty()) {
                prompt.add("EN BUYUK POZISYONLAR (sadece baglam icin; listeleme veya detayli analiz yapma)");
                for (DashboardAiInputContext.HoldingSnapshot h : context.topHoldings()) {
                    prompt.add("- " + h.symbol()
                            + " | tip=" + h.instrumentType()
                            + " | agirlik%=" + fmt(h.weightPercent())
                            + " | gunluk degisim%=" + fmt(h.dailyChangePercent()));
                }
                prompt.add("");
            }
        } else {
            prompt.add("PORTFOY: Kullanicinin kayitli portfoyu yok veya bos. portfolioImpact alanini null veya bos string dondur.");
            prompt.add("");
        }

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