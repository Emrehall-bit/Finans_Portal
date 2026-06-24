package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;

import com.emrehalli.financeportal.ai.features.fundamental.FinancialInsight;
import com.emrehalli.financeportal.ai.features.technical.TechnicalInsight;
import com.emrehalli.financeportal.ai.features.technical.TechnicalInsightGenerator;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TechnicalAnalysisPromptBuilder implements AiPromptBuilder {

    private final TechnicalInsightGenerator insightGenerator;

    public TechnicalAnalysisPromptBuilder(TechnicalInsightGenerator insightGenerator) {
        this.insightGenerator = insightGenerator;
    }

    public String build(String symbol, TechnicalAnalysisResult analysis, String language) {
        TechnicalInsight insight = insightGenerator.generate(symbol, analysis);
        String insightBlock = formatSignals(insight);

        BigDecimal rsi   = analysis.indicatorValues().get(IndicatorType.RSI14);
        BigDecimal price = analysis.latestPrice();

        return AiPromptBuilder.languageInstruction(language) + """
                Sen Finance Portal'ın uzman teknik analiz asistanısın. %s hissesi için aşağıdaki \
                önceden yorumlanmış sinyalleri bir araya getirerek kısa ve profesyonel bir analiz yaz.
                Sinyalleri birebir tekrar etme; sentezle ve birbirine bağla.
                Finansal analist dili kullan. "Bakılabilir", "incelenebilir" gibi muğlak ifade kullanma.
                Hareketli ortalama değerlerini zaten yorumladık; sadece anlamını pekiştir.

                ÖNCEDEN YORUMLANMIŞ SİNYALLER:
                %s

                TREND ÖZETİ: %s
                MOMENTUM ÖZETİ: %s

                BAĞLAM (çapraz doğrulama için - sinyal metinlerini tekrar etme):
                Fiyat: %s TL | RSI(14): %s

                ANTİ-HALÜSİNASYON:
                - Yalnızca yukarıdaki verileri kullan; şirketin haberlerini veya bilinmeyen bilgilerini ekleme.
                - Spesifik fiyat hedefi verme.
                - Veri yoksa ("-") o konuya girme.

                YANIT FORMATI - sadece bu JSON, başka hiçbir şey:
                {"summary":"2-3 cümle: fiyat-trend durumu + öne çıkan sinyal + kısa vadeli risk veya fırsat",\
                "trendComment":"1-2 cümle: hareketli ortalamalarla ilişkinin ne anlama geldiği (gerekirse bir sayı)",\
                "momentumComment":"1-2 cümle: RSI yorumu ve trend ile tutarlılığı",\
                "keyObservation":"Bu enstrüman için şu an en kritik teknik gözlem nedir? Tek, net cümle.",\
                "riskLevel":"LOW veya MEDIUM veya HIGH","signal":"POSITIVE veya NEUTRAL veya NEGATIVE veya RISKY"}
                """.formatted(
                symbol,
                insightBlock,
                insight.trendSummary(),
                insight.momentumSummary(),
                val(price),
                val(rsi)
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
