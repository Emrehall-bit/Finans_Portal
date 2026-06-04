package com.emrehalli.financeportal.ai.features.fundamental;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;

import com.emrehalli.financeportal.ai.features.fundamental.FinancialInsight;
import com.emrehalli.financeportal.ai.features.fundamental.FundamentalInsightGenerator;
import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FundamentalAnalysisPromptBuilder implements AiPromptBuilder {

    private final FundamentalInsightGenerator insightGenerator;

    public FundamentalAnalysisPromptBuilder(FundamentalInsightGenerator insightGenerator) {
        this.insightGenerator = insightGenerator;
    }

    public String build(String symbol,
                        CompanyFundamentalsResponse fundamentals,
                        BigDecimal revenue,
                        BigDecimal netProfit,
                        String language) {
        List<FinancialInsight> insights = insightGenerator.generate(fundamentals, revenue, netProfit);

        String strengths  = block(insights, FinancialInsight.Category.STRENGTH);
        String weaknesses = block(insights, FinancialInsight.Category.WEAKNESS);
        String risks      = block(insights, FinancialInsight.Category.RISK);
        String growth     = block(insights, FinancialInsight.Category.GROWTH);
        String valuation  = block(insights, FinancialInsight.Category.VALUATION);
        String neutral    = block(insights, FinancialInsight.Category.NEUTRAL);

        return AiPromptBuilder.languageInstruction(language) + """
                Sen Finance Portal'in uzman temel analiz asistanisin. %s sirketi icin asagidaki \
                onceden yorumlanmis finansal sinyalleri sentezleyerek kisa ve profesyonel bir analiz yaz.
                Sinyalleri birebir tekrar etme; sentezle ve birlestir.
                Finansal analist dili kullan. "Bakilabilir", "incelenebilir" ifadesi kullanma; direkt yorum yap.

                ONCEDEN YORUMLANMIS FINANSAL SINYALLER:
                [GUC]     %s
                [ZAYIF]   %s
                [RISK]    %s
                [BUYUME]  %s
                [DEGER]   %s
                [NOTR]    %s

                BAGLAN (dogrulama icin - sinyalleri tekrar etme):
                Donem: %s | Hasilat: %s TL | Net Kar: %s TL

                ANTI-HALUSINASYON:
                - Yalnizca yukardaki verileri kullan. Degeri "-" olan alanlar icin sayi uydurma.
                - Sirketin sektorunu veya haberlerini ekleme.
                - Olmayan veriyi tahminen belirtme.

                CIKTI YAPISI - alti bolum, tek JSON nesnesi:
                1. summary: 2-3 cumle genel finansal gorunum - sayilari tekrar etme, finansal tablonun hikayesini anlat
                2. strengths: 2-3 madde - sirketin icsel guclu dinamikleri; gerekirse bir sayi kullan ama asil amac anlami aciklamak
                3. weaknesses: 1-2 madde - sirket icindeki zayif noktalar
                4. risks: 1-2 madde - bilanco kirilganligi, dis faktorler veya surdurulebilirlik belirsizligi; strengths/weaknesses ile tekrar etme
                5. growthComment: 2-3 cumle buyume yorumu - sadece oranlari listeleme, buyumenin kalitesini yorumla
                6. financialHealth: STRONG | STABLE | WATCH | RISKY

                YANIT FORMATI - sadece bu JSON, baska hicbir sey:
                {"summary":"...","strengths":["...","..."],"weaknesses":["..."],"risks":["..."],"growthComment":"...","financialHealth":"STABLE"}
                """.formatted(
                symbol,
                strengths, weaknesses, risks, growth, valuation, neutral,
                ns(fundamentals.getLatestReportPeriod()),
                val(revenue), val(netProfit)
        );
    }

    private String block(List<FinancialInsight> insights, FinancialInsight.Category category) {
        String joined = insights.stream()
                .filter(i -> i.category() == category)
                .map(FinancialInsight::text)
                .collect(Collectors.joining(" | "));
        return joined.isBlank() ? "-" : joined;
    }

    private String val(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private String ns(String s) {
        return s != null ? s : "-";
    }
}
