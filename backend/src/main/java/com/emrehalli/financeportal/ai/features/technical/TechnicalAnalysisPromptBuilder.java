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
                Sen Finance Portal'in uzman teknik analiz asistanisin. %s hissesi icin asagidaki \
                onceden yorumlanmis sinyalleri bir araya getirerek kisa ve profesyonel bir analiz yaz.
                Sinyalleri birebir tekrar etme; sentezle ve birbirine bagla.
                Finansal analist dili kullan. "Bakilabilir", "incelenebilir" gibi mugak ifade kullanma.
                Hareketli ortalama degerlerini zaten yorumladik; sadece anlamini pekistir.

                ONCEDEN YORUMLANMIS SINYALLER:
                %s

                TREND OZETI: %s
                MOMENTUM OZETI: %s

                BAGLAN (capraz dogrulama icin - sinyal metinlerini tekrar etme):
                Fiyat: %s TL | RSI(14): %s

                ANTI-HALUSINASYON:
                - Yalnizca yukardaki verileri kullan; sirketin haberlerini veya bilinmeyen bilgilerini ekleme.
                - Spesifik fiyat hedefi verme.
                - Veri yoksa ("-") o konuya girme.

                YANIT FORMATI - sadece bu JSON, baska hicbir sey:
                {"summary":"2-3 cumle: fiyat-trend durumu + one cikan sinyal + kisa vadeli risk veya firsat",\
                "trendComment":"1-2 cumle: hareketli ortalamalarla iliskinin ne anlama geldigi (gerekirse bir sayi)",\
                "momentumComment":"1-2 cumle: RSI yorumu ve trend ile tutarliligi",\
                "keyObservation":"Bu enstruman icin su an en kritik teknik gozlem nedir? Tek, net cumle.",\
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
        if (insight.signals().isEmpty()) return "Yeterli sinyal uretilemedi.";
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
