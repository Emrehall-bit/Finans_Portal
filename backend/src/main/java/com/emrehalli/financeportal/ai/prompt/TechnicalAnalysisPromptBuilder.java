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
                Sen Finance Portal'Ä±n uzman teknik analiz asistanÄ±sÄ±n. %s hissesi iÃ§in aÅŸaÄŸÄ±daki \
                Ã¶nceden yorumlanmÄ±ÅŸ sinyalleri bir araya getirerek kÄ±sa ve profesyonel bir analiz yaz.
                TÃ¼rkÃ§e yaz. Sinyalleri birebir tekrar etme; sentezle ve birbirine baÄŸla.
                Finansal analist dili kullan. "BakÄ±labilir", "incelenebilir" gibi muÄŸlak ifade kullanma.

                Ã–NCEDEn YORUMLANMÄ±ÅŸ SÄ°NYALLER:
                %s

                TREND Ã–ZETÄ°: %s
                MOMENTUM Ã–ZETÄ°: %s

                HAM VERÄ°LER (Ã§apraz doÄŸrulama iÃ§in â€” tekrar etme):
                Fiyat: %s TL | Trend: %s | RSI(14): %s | SMA7: %s | SMA20: %s | SMA50: %s

                ANTÄ°-HALÃœSÄ°NASYON:
                - YalnÄ±zca yukarÄ±daki verileri kullan; ÅŸirketin haberlerini veya bilinmeyen bilgilerini ekleme.
                - Spesifik fiyat hedefi verme.
                - Veri yoksa ("-") o konuya girme.

                YANIT FORMATI â€” sadece bu JSON, baÅŸka hiÃ§bir ÅŸey:
                {"summary":"2-3 cÃ¼mle: fiyat-trend durumu + Ã¶ne Ã§Ä±kan sinyal + kÄ±sa vadeli risk veya fÄ±rsat",\
                "trendComment":"1-2 cÃ¼mle: hareketli ortalamalarla iliÅŸki (rakam kullan)",\
                "momentumComment":"1-2 cÃ¼mle: RSI yorumu ve trend ile tutarlÄ±lÄ±ÄŸÄ±",\
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
        if (insight.signals().isEmpty()) return "Yeterli sinyal Ã¼retilemedi.";
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




