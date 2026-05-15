package com.emrehalli.financeportal.ai.prompt;

import com.emrehalli.financeportal.ai.insight.FinancialInsight;
import com.emrehalli.financeportal.ai.insight.TechnicalInsight;
import com.emrehalli.financeportal.ai.insight.TechnicalInsightGenerator;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TechnicalAnalysisPromptBuilder implements AiPromptBuilder {

    private final TechnicalInsightGenerator insightGenerator;

    public TechnicalAnalysisPromptBuilder(TechnicalInsightGenerator insightGenerator) {
        this.insightGenerator = insightGenerator;
    }

    public String build(String symbol, TechnicalAnalysisResult analysis) {
        TechnicalInsight insight = insightGenerator.generate(symbol, analysis);
        String insightBlock = formatSignals(insight);

        BigDecimal rsi  = analysis.indicatorValues().get(IndicatorType.RSI14);
        BigDecimal sma7  = analysis.indicatorValues().get(IndicatorType.SMA7);
        BigDecimal sma20 = analysis.indicatorValues().get(IndicatorType.SMA20);
        BigDecimal sma50 = analysis.indicatorValues().get(IndicatorType.SMA50);

        return """
                Sen Finance Portal'ın uzman teknik analiz asistanısın. %s hissesi için aşağıdaki \
                önceden yorumlanmış sinyalleri bir araya getirerek kısa ve profesyonel bir analiz yaz.
                Türkçe yaz. Sinyalleri birebir tekrar etme; sentezle ve birbirine bağla.
                Finansal analist dili kullan. "Bakılabilir", "incelenebilir" gibi muğlak ifade kullanma.

                ÖNCEDEn YORUMLANMış SİNYALLER:
                %s

                TREND ÖZETİ: %s
                MOMENTUM ÖZETİ: %s

                HAM VERİLER (çapraz doğrulama için — tekrar etme):
                Fiyat: %s TL | Trend: %s | RSI(14): %s | SMA7: %s | SMA20: %s | SMA50: %s

                ANTİ-HALÜSİNASYON:
                - Yalnızca yukarıdaki verileri kullan; şirketin haberlerini veya bilinmeyen bilgilerini ekleme.
                - Spesifik fiyat hedefi verme.
                - Veri yoksa ("-") o konuya girme.

                YANIT FORMATI — sadece bu JSON, başka hiçbir şey:
                {"summary":"2-3 cümle: fiyat-trend durumu + öne çıkan sinyal + kısa vadeli risk veya fırsat",\
                "trendComment":"1-2 cümle: hareketli ortalamalarla ilişki (rakam kullan)",\
                "momentumComment":"1-2 cümle: RSI yorumu ve trend ile tutarlılığı",\
                "riskLevel":"LOW veya MEDIUM veya HIGH","signal":"POSITIVE veya NEUTRAL veya NEGATIVE veya RISKY"}
                """.formatted(
                symbol,
                insightBlock,
                insight.trendSummary(),
                insight.momentumSummary(),
                val(analysis.latestPrice()),
                analysis.trendDirection(),
                val(rsi), val(sma7), val(sma20), val(sma50)
        );
    }

    private String formatSignals(TechnicalInsight insight) {
        if (insight.signals().isEmpty()) return "Yeterli sinyal üretilemedi.";
        StringBuilder sb = new StringBuilder();
        for (FinancialInsight fi : insight.signals()) {
            String tag = switch (fi.category()) {
                case STRENGTH  -> "[+]";
                case WEAKNESS  -> "[-]";
                case RISK      -> "[!]";
                default        -> "[~]";
            };
            sb.append(tag).append(" ").append(fi.text()).append("\n");
        }
        return sb.toString().trim();
    }

    private String val(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}
