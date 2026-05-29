package com.emrehalli.financeportal.ai.prompt;

import com.emrehalli.financeportal.ai.insight.FinancialInsight;
import com.emrehalli.financeportal.ai.insight.FundamentalInsightGenerator;
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

    /**
     * @param revenue   latest period revenue (may be null)
     * @param netProfit latest period net profit (may be null)
     */
    public String build(String symbol,
                        CompanyFundamentalsResponse fundamentals,
                        BigDecimal revenue,
                        BigDecimal netProfit) {
        List<FinancialInsight> insights = insightGenerator.generate(fundamentals, revenue, netProfit);

        String strengths  = block(insights, FinancialInsight.Category.STRENGTH);
        String weaknesses = block(insights, FinancialInsight.Category.WEAKNESS);
        String risks      = block(insights, FinancialInsight.Category.RISK);
        String growth     = block(insights, FinancialInsight.Category.GROWTH);
        String valuation  = block(insights, FinancialInsight.Category.VALUATION);
        String neutral    = block(insights, FinancialInsight.Category.NEUTRAL);

        return """
                Sen Finance Portal'Ä±n uzman temel analiz asistanÄ±sÄ±n. %s ÅŸirketi iÃ§in aÅŸaÄŸÄ±daki \
                Ã¶nceden yorumlanmÄ±ÅŸ finansal sinyalleri sentezleyerek kÄ±sa ve profesyonel bir analiz yaz.
                TÃ¼rkÃ§e yaz. Sinyalleri birebir tekrar etme; sentezle ve birleÅŸtir.
                Finansal analist dili kullan. "BakÄ±labilir", "incelenebilir" ifadesi kullanma; direkt yorum yap.

                Ã–NCEDEn YORUMLANMÄ±ÅŸ FÄ°NANSAL SÄ°NYALLER:
                [GÃœÃ‡]     %s
                [ZAYIF]   %s
                [RÄ°SK]    %s
                [BÃœYÃœME]  %s
                [DEÄER]   %s
                [NÃ–TR]    %s

                HAM VERÄ°LER (Ã§apraz doÄŸrulama iÃ§in â€” sinyal metinlerini birebir tekrar etme):
                DÃ¶nem: %s | HasÄ±lat: %s TL | Net KÃ¢r: %s TL
                ROE: %s | ROA: %s | Net Marj: %s | BrÃ¼t Marj: %s
                F/K: %s | PD/DD: %s | D/E: %s
                HasÄ±lat bÃ¼yÃ¼mesi: %s (%s) | Net kÃ¢r bÃ¼yÃ¼mesi: %s (%s)

                ANTÄ°-HALÃœSÄ°NASYON:
                - YalnÄ±zca yukarÄ±daki verileri kullan. DeÄŸeri "-" olan alanlar iÃ§in sayÄ± uydurma.
                - Åirketin sektÃ¶rÃ¼nÃ¼ veya haberlerini ekleme.
                - Olmayan veriyi tahminen belirtme.

                Ã‡IKTI YAPISI â€” altÄ± bÃ¶lÃ¼m, tek JSON nesnesi:
                1. summary: 2-3 cÃ¼mle genel finansal gÃ¶rÃ¼nÃ¼m
                2. strengths: 2-3 madde, rakamla destekli, spesifik
                3. weaknesses: 1-2 madde, spesifik oran ile destekli
                4. risks: 1-2 madde, veri destekli
                5. growthComment: 2-3 cÃ¼mle bÃ¼yÃ¼me yorumu
                6. financialHealth: STRONG | STABLE | WATCH | RISKY

                YANIT FORMATI â€” sadece bu JSON, baÅŸka hiÃ§bir ÅŸey:
                {"summary":"...","strengths":["...","..."],"weaknesses":["..."],"risks":["..."],"growthComment":"...","financialHealth":"STABLE"}
                """.formatted(
                symbol,
                strengths, weaknesses, risks, growth, valuation, neutral,
                ns(fundamentals.getLatestReportPeriod()),
                val(revenue), val(netProfit),
                val(fundamentals.getRoe()),      val(fundamentals.getRoa()),
                val(fundamentals.getNetMargin()), val(fundamentals.getGrossMargin()),
                val(fundamentals.getPeRatio()),   val(fundamentals.getPbRatio()),
                val(fundamentals.getDebtToEquity()),
                val(fundamentals.getRevenueGrowth()),    ns(fundamentals.getRevenueGrowthLabel()),
                val(fundamentals.getNetProfitGrowth()),  ns(fundamentals.getNetProfitGrowthLabel())
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




