package com.emrehalli.financeportal.ai.prompt;

import com.emrehalli.financeportal.ai.insight.FinancialInsight;
import com.emrehalli.financeportal.ai.insight.FundamentalInsightGenerator;
import com.emrehalli.financeportal.company.dto.CompanyFundamentalsResponse;
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
                Sen Finance Portal'ın uzman temel analiz asistanısın. %s şirketi için aşağıdaki \
                önceden yorumlanmış finansal sinyalleri sentezleyerek kısa ve profesyonel bir analiz yaz.
                Türkçe yaz. Sinyalleri birebir tekrar etme; sentezle ve birleştir.
                Finansal analist dili kullan. "Bakılabilir", "incelenebilir" ifadesi kullanma; direkt yorum yap.

                ÖNCEDEn YORUMLANMış FİNANSAL SİNYALLER:
                [GÜÇ]     %s
                [ZAYIF]   %s
                [RİSK]    %s
                [BÜYÜME]  %s
                [DEĞER]   %s
                [NÖTR]    %s

                HAM VERİLER (çapraz doğrulama için — sinyal metinlerini birebir tekrar etme):
                Dönem: %s | Hasılat: %s TL | Net Kâr: %s TL
                ROE: %s | ROA: %s | Net Marj: %s | Brüt Marj: %s
                F/K: %s | PD/DD: %s | D/E: %s
                Hasılat büyümesi: %s (%s) | Net kâr büyümesi: %s (%s)

                ANTİ-HALÜSİNASYON:
                - Yalnızca yukarıdaki verileri kullan. Değeri "-" olan alanlar için sayı uydurma.
                - Şirketin sektörünü veya haberlerini ekleme.
                - Olmayan veriyi tahminen belirtme.

                ÇIKTI YAPISI — altı bölüm, tek JSON nesnesi:
                1. summary: 2-3 cümle genel finansal görünüm
                2. strengths: 2-3 madde, rakamla destekli, spesifik
                3. weaknesses: 1-2 madde, spesifik oran ile destekli
                4. risks: 1-2 madde, veri destekli
                5. growthComment: 2-3 cümle büyüme yorumu
                6. financialHealth: STRONG | STABLE | WATCH | RISKY

                YANIT FORMATI — sadece bu JSON, başka hiçbir şey:
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
