package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.prompt.FundamentalAnalysisPromptBuilder;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.company.dto.response.CompanyFinancialReportResponse;
import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.dto.response.FinancialValueItemResponse;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiFundamentalAnalysisService {

    private static final Logger logger = LogManager.getLogger(AiFundamentalAnalysisService.class);
    private static final String DISCLAIMER = "Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir; yalnÄ±zca mevcut verilerin otomatik analizidir.";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofMinutes(30);

    private final CompanyQueryService companyQueryService;
    private final AiGenerationService aiGenerationService;
    private final AiResponseCacheService aiResponseCacheService;
    private final FundamentalAnalysisPromptBuilder promptBuilder;
    private final AiResponseLogHelper responseLogHelper;

    public AiFundamentalAnalysisService(CompanyQueryService companyQueryService,
                                        AiGenerationService aiGenerationService,
                                        AiResponseCacheService aiResponseCacheService,
                                        FundamentalAnalysisPromptBuilder promptBuilder,
                                        AiResponseLogHelper responseLogHelper) {
        this.companyQueryService = companyQueryService;
        this.aiGenerationService = aiGenerationService;
        this.aiResponseCacheService = aiResponseCacheService;
        this.promptBuilder = promptBuilder;
        this.responseLogHelper = responseLogHelper;
    }

    public AiFundamentalAnalysisResponse getFundamentalComment(String symbol) {
        return getFundamentalComment(symbol, "tr");
    }

    public AiFundamentalAnalysisResponse getFundamentalComment(String symbol, String language) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String lang = AiPromptBuilder.normLang(language);
        String cacheKey = "ai:fundamental:" + normalizedSymbol + ":" + lang;
        try {
            LookupResult<AiFundamentalAnalysisResponse> lookup = aiResponseCacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, AiFundamentalAnalysisResponse.class, () -> computeFundamentalComment(normalizedSymbol, lang));
            AiFundamentalAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.FUNDAMENTAL_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI fundamental endpoint critical failure, returning fallback. symbol={}, reason={}", normalizedSymbol, exception.getMessage());
            AiFundamentalAnalysisResponse response = dataLimitedFallback(normalizedSymbol, lang);
            responseLogHelper.log(AiTaskType.FUNDAMENTAL_ANALYSIS, response.metadata());
            return response;
        }
    }

    private CachedValue<AiFundamentalAnalysisResponse> computeFundamentalComment(String normalizedSymbol, String lang) {
        try {
            CompanyFundamentalsResponse fundamentals = companyQueryService.getFundamentals(normalizedSymbol);
            if (fundamentals == null || fundamentals.getMessage() != null) {
                logger.info("AI fundamental computed. symbol={}, source=RULE_BASED_FALLBACK, reason=no-fundamentals-data, ttlMinutes={}",
                        normalizedSymbol, FALLBACK_CACHE_TTL.toMinutes());
                return new CachedValue<>(dataLimitedFallback(normalizedSymbol, lang), FALLBACK_CACHE_TTL);
            }

            LatestFinancials latestFinancials = extractLatestFinancials(companyQueryService.getFinancials(normalizedSymbol));
            AiFundamentalAnalysisResponse ruleBased = fromFundamentals(normalizedSymbol, fundamentals, latestFinancials, lang);
            AiGenerationService.EnhancedResult<AiFundamentalAnalysisResponse> enhanced =
                    aiGenerationService.enhanceFundamental(
                            promptBuilder.build(normalizedSymbol, fundamentals, latestFinancials.revenue(), latestFinancials.netProfit(), lang),
                            ruleBased);
            Duration ttl = enhanced.fromLlm() ? CACHE_TTL : FALLBACK_CACHE_TTL;
            String summarySnippet = enhanced.response().summary();
            logger.info("AI fundamental computed. symbol={}, source={}, provider={}, ttlMinutes={}, summaryPreview={}",
                    normalizedSymbol,
                    enhanced.fromLlm() ? "LLM_SUCCESS" : "RULE_BASED_FALLBACK",
                    enhanced.metadata() != null ? enhanced.metadata().providerUsed() : null,
                    ttl.toMinutes(),
                    summarySnippet != null && summarySnippet.length() > 100 ? summarySnippet.substring(0, 100) : summarySnippet);
            return new CachedValue<>(enhanced.response(), ttl);
        } catch (Exception exception) {
            logger.warn("AI fundamental analysis used data-limited fallback. symbol={}, source=RULE_BASED_FALLBACK, reason={}", normalizedSymbol, exception.getMessage());
            return new CachedValue<>(dataLimitedFallback(normalizedSymbol, lang), FALLBACK_CACHE_TTL);
        }
    }

    private AiFundamentalAnalysisResponse fromFundamentals(String symbol,
                                                          CompanyFundamentalsResponse fundamentals,
                                                          LatestFinancials latestFinancials,
                                                          String lang) {
        FinancialHealth health = resolveHealth(fundamentals, latestFinancials);
        List<String> strengths = buildStrengths(fundamentals, latestFinancials, lang);
        List<String> weaknesses = buildWeaknesses(fundamentals, latestFinancials, lang);
        List<String> risks = buildRisks(fundamentals, latestFinancials, lang);
        String growthComment = buildGrowthComment(fundamentals, lang);
        String summary = buildSummary(symbol, fundamentals, latestFinancials, health, lang);

        return new AiFundamentalAnalysisResponse(
                symbol,
                summary,
                strengths,
                weaknesses,
                risks,
                growthComment,
                health,
                DISCLAIMER,
                AiResponseMetadata.deterministic("FULL")
        );
    }

    private FinancialHealth resolveHealth(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        int score = 0;
        int riskFlags = 0;

        if (greaterThan(fundamentals.getRevenueGrowth(), "0")) score++;
        if (greaterThan(fundamentals.getNetProfitGrowth(), "0")) score++;
        if (greaterThan(fundamentals.getRoe(), "0.15")) score += 2;
        if (greaterThan(fundamentals.getRoa(), "0.04")) score++;
        if (greaterThan(fundamentals.getGrossMargin(), "0.15")) score++;
        if (greaterThan(fundamentals.getNetMargin(), "0.05")) score++;
        if (between(fundamentals.getPeRatio(), "0", "15")) score++;
        if (between(fundamentals.getPbRatio(), "0", "2")) score++;

        if (lessThan(fundamentals.getRevenueGrowth(), "0")) riskFlags++;
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) riskFlags++;
        if (lessThanOrEqual(fundamentals.getNetMargin(), "0")) riskFlags++;
        if (lessThanOrEqual(latestFinancials.netProfit(), "0")) riskFlags++;
        if (greaterThan(fundamentals.getDebtToEquity(), "2")) riskFlags++;
        if (containsTurkish(fundamentals.getNetProfitGrowthLabel(), "zarar")) riskFlags++;

        if (riskFlags >= 3) return FinancialHealth.RISKY;
        if (riskFlags >= 1 && score < 4) return FinancialHealth.WATCH;
        if (score >= 6) return FinancialHealth.STRONG;
        if (score >= 3) return FinancialHealth.STABLE;
        return FinancialHealth.WATCH;
    }

    private List<String> buildStrengths(CompanyFundamentalsResponse fundamentals,
                                        LatestFinancials latestFinancials, String lang) {
        boolean en = "en".equals(lang);
        List<String> strengths = new ArrayList<>();
        if (greaterThan(fundamentals.getRevenueGrowth(), "0"))
            strengths.add(en ? "Revenue growth is positive; sales momentum looks solid."
                             : "Hasılat büyümesi pozitif; satış tarafında büyüme güçlü görünüyor.");
        if (greaterThan(fundamentals.getNetProfitGrowth(), "0"))
            strengths.add(en ? "Net profit growth is positive; profitability is supported on a year-over-year basis."
                             : "Net kâr büyümesi pozitif; kârlılık yıllık bazda destekleniyor.");
        if (greaterThan(fundamentals.getRoe(), "0.15"))
            strengths.add(en ? "ROE is high; equity profitability is strong."
                             : "ROE yüksek; özkaynak kârlılığı güçlü.");
        if (greaterThan(fundamentals.getRoa(), "0.04"))
            strengths.add(en ? "ROA is positive and meaningful; asset return capacity is supportive."
                             : "ROA pozitif ve anlamlı; varlıkların kâr üretme gücü destekleyici.");
        if (greaterThan(fundamentals.getGrossMargin(), "0.15"))
            strengths.add(en ? "Gross margin supports operational profitability."
                             : "Brüt marj operasyonel kârlılığı destekliyor.");
        if (greaterThan(fundamentals.getNetMargin(), "0.05"))
            strengths.add(en ? "Net margin is positive; the company generates profit from sales."
                             : "Net marj pozitif; şirket satışlarından kâr üretebiliyor.");
        if (between(fundamentals.getPeRatio(), "0", "15"))
            strengths.add(en ? "P/E ratio is in a reasonable range relative to current earnings."
                             : "F/K oranı mevcut kârlılığa göre makul bölgede.");
        if (between(fundamentals.getPbRatio(), "0", "2"))
            strengths.add(en ? "P/B ratio does not signal excessive overvaluation relative to book value."
                             : "PD/DD oranı özkaynak değerlemesine göre aşırı pahalı sinyal vermiyor.");
        if (greaterThan(latestFinancials.netProfit(), "0"))
            strengths.add(en ? "Net profit was positive in the latest report."
                             : "Son raporda net kâr pozitif.");
        if (strengths.isEmpty())
            strengths.add(en ? "Available data does not generate a strong positive signal; additional period data should be monitored."
                             : "Mevcut veriler güçlü bir pozitif sinyal üretmiyor; daha fazla dönem verisi izlenmeli.");
        return List.copyOf(strengths);
    }

    private List<String> buildWeaknesses(CompanyFundamentalsResponse fundamentals,
                                         LatestFinancials latestFinancials, String lang) {
        boolean en = "en".equals(lang);
        List<String> weaknesses = new ArrayList<>();
        if (fundamentals.getPeRatio() == null)
            weaknesses.add(en ? "P/E cannot be calculated; net profit or market value data may be missing."
                              : "F/K hesaplanamıyor; net kâr veya piyasa değeri verisi eksik olabilir.");
        else if (greaterThan(fundamentals.getPeRatio(), "25"))
            weaknesses.add(en ? "P/E is high; a significant portion of earnings expectations may already be priced in."
                              : "F/K yüksek; kâr beklentilerinin önemli kısmı fiyata yansımış olabilir.");
        if (fundamentals.getPbRatio() == null)
            weaknesses.add(en ? "P/B cannot be calculated; equity or market value data may be missing."
                              : "PD/DD hesaplanamıyor; özkaynak veya piyasa değeri verisi eksik olabilir.");
        else if (greaterThan(fundamentals.getPbRatio(), "3"))
            weaknesses.add(en ? "P/B is high; pricing may be at a premium to book value."
                              : "PD/DD yüksek; defter değerine göre primli fiyatlama olabilir.");
        if (lessThan(fundamentals.getRevenueGrowth(), "0"))
            weaknesses.add(en ? "Revenue growth is negative; sales-side pressure is present."
                              : "Hasılat büyümesi negatif; satış tarafında baskı var.");
        if (lessThan(fundamentals.getNetProfitGrowth(), "0"))
            weaknesses.add(en ? "Net profit growth is negative; profitability momentum is weakening."
                              : "Net kâr büyümesi negatif; kârlılık ivmesi zayıflıyor.");
        if (lessThanOrEqual(fundamentals.getNetMargin(), "0") || lessThanOrEqual(latestFinancials.netProfit(), "0"))
            weaknesses.add(en ? "Net profit or net margin is negative; a financial pressure signal is forming."
                              : "Net kâr veya net marj negatif; finansal baskı sinyali oluşuyor.");
        if (weaknesses.isEmpty())
            weaknesses.add(en ? "No significant weakness detected; ratios should still be compared against the sector average."
                              : "Belirgin zayıflık sınırlı; oranlar yine de sektör ortalamasıyla karşılaştırılmalı.");
        return List.copyOf(weaknesses);
    }

    private List<String> buildRisks(CompanyFundamentalsResponse fundamentals,
                                    LatestFinancials latestFinancials, String lang) {
        boolean en = "en".equals(lang);
        List<String> risks = new ArrayList<>();
        if (greaterThan(fundamentals.getDebtToEquity(), "2"))
            risks.add(en ? "Debt-to-equity ratio is high; financing cost and balance sheet risk may increase."
                        : "Borç/özkaynak oranı yüksek; finansman maliyeti ve bilanço riski artabilir.");
        if (lessThanOrEqual(latestFinancials.netProfit(), "0"))
            risks.add(en ? "Net profit was negative or zero in the latest report; profitability sustainability should be monitored."
                        : "Son raporda net kâr negatif veya sıfır; kârlılık sürdürülebilirliği izlenmeli.");
        if (lessThan(fundamentals.getRevenueGrowth(), "0"))
            risks.add(en ? "Negative revenue growth may signal operational demand or pricing pressure."
                        : "Negatif hasılat büyümesi operasyonel talep veya fiyatlama baskısına işaret edebilir.");
        if (lessThan(fundamentals.getNetProfitGrowth(), "0"))
            risks.add(en ? "Negative net profit growth may indicate margin or cost pressure."
                        : "Negatif net kâr büyümesi marj veya maliyet baskısı yaratabilir.");
        if (fundamentals.getRevenueGrowth() == null && fundamentals.getRevenueGrowthLabel() == null)
            risks.add(en ? "Insufficient comparable period data for revenue growth assessment."
                        : "Hasılat büyümesi için yeterli karşılaştırmalı dönem verisi yok.");
        if (fundamentals.getNetProfitGrowth() == null && fundamentals.getNetProfitGrowthLabel() == null)
            risks.add(en ? "Insufficient comparable period data for net profit growth assessment."
                        : "Net kâr büyümesi için yeterli karşılaştırmalı dönem verisi yok.");
        if (risks.isEmpty())
            risks.add(en ? "Key risks are macro conditions, sector cycle and financial data currency."
                        : "Ana riskler makro koşullar, sektör döngüsü ve finansal veri güncelliğidir.");
        return List.copyOf(risks);
    }

    private String buildGrowthComment(CompanyFundamentalsResponse fundamentals, String lang) {
        boolean en = "en".equals(lang);
        List<String> comments = new ArrayList<>();
        comments.add(growthText(en ? "Revenue"    : "Hasılat",  fundamentals.getRevenueGrowth(),   fundamentals.getRevenueGrowthLabel(), lang));
        comments.add(growthText(en ? "Net profit" : "Net kâr",  fundamentals.getNetProfitGrowth(), fundamentals.getNetProfitGrowthLabel(), lang));
        comments.add(growthText(en ? "Assets"     : "Aktifler", fundamentals.getAssetGrowth(),     fundamentals.getAssetGrowthLabel(), lang));
        return String.join(" ", comments);
    }

    private String buildSummary(String symbol,
                                CompanyFundamentalsResponse fundamentals,
                                LatestFinancials latestFinancials,
                                FinancialHealth health,
                                String lang) {
        boolean en = "en".equals(lang);

        String netProfitContext = latestFinancials.netProfit() == null
                ? (en ? "net profit data unavailable"     : "net kâr verisi yetersiz")
                : latestFinancials.netProfit().compareTo(BigDecimal.ZERO) < 0
                ? (en ? "profitability is under pressure" : "kârlılık baskı altında")
                : (en ? "profitability appears supportive": "kârlılık destekleyici görünüyor");

        String healthContext = en
                ? switch (health) {
                    case STRONG -> "financial indicators are broadly strong";
                    case STABLE -> "financial position is balanced";
                    case WATCH  -> "some indicators warrant close monitoring";
                    case RISKY  -> "risk signals are prominent in the financial picture";
                  }
                : switch (health) {
                    case STRONG -> "finansal göstergeler genel olarak güçlü";
                    case STABLE -> "finansal tablo dengeli seyrediyor";
                    case WATCH  -> "bazı göstergeler yakın takip gerektiriyor";
                    case RISKY  -> "finansal tabloda risk sinyalleri öne çıkıyor";
                  };

        return en
                ? symbol + " fundamental assessment: " + healthContext + "; " + netProfitContext + ". "
                    + "Growth, profitability and leverage data are the key inputs to this assessment."
                : symbol + " için kural tabanlı temel analiz: " + healthContext + "; " + netProfitContext + ". "
                    + "Büyüme, kârlılık ve borçluluk verileri bu değerlendirmenin temel girdilerini oluşturuyor.";
    }

    private LatestFinancials extractLatestFinancials(List<CompanyFinancialReportResponse> reports) {
        if (reports == null || reports.isEmpty()) {
            return new LatestFinancials(null, null);
        }
        CompanyFinancialReportResponse latest = reports.stream()
                .filter(report -> report.getValues() != null && !report.getValues().isEmpty())
                .findFirst()
                .orElse(null);
        if (latest == null) {
            return new LatestFinancials(null, null);
        }
        return new LatestFinancials(
                effectiveValue(findValue(latest, "HASILAT")),
                effectiveValue(findValue(latest, "NET_DONEM_KARI"))
        );
    }

    private FinancialValueItemResponse findValue(CompanyFinancialReportResponse report, String itemKey) {
        return report.getValues().stream()
                .filter(value -> itemKey.equals(value.getItemKey()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal effectiveValue(FinancialValueItemResponse value) {
        if (value == null || value.getValue() == null) {
            return null;
        }
        return value.getValue().multiply(BigDecimal.valueOf(value.getUnitMultiplier() != null ? value.getUnitMultiplier() : 1));
    }

    private AiFundamentalAnalysisResponse dataLimitedFallback(String symbol, String lang) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        FinancialHealth health = bucket == 0 ? FinancialHealth.STABLE : bucket == 1 ? FinancialHealth.WATCH : FinancialHealth.RISKY;
        boolean en = "en".equals(lang);
        return new AiFundamentalAnalysisResponse(
                symbol,
                en ? symbol + ": fundamental data is limited; assessment is inconclusive pending complete financial reports."
                   : symbol + " icin temel analiz verisi sinirli. Rule-based yorum, oranlar hesaplanana kadar veri eksikligini ana risk olarak degerlendirir.",
                en ? List.of("Once financial data is complete, growth, margin and valuation analysis will be available.")
                   : List.of("Finansal veriler tamamlandiginda buyume, marj ve degerleme kurallari otomatik yorumlanabilir."),
                en ? List.of("P/E, P/B, ROE, ROA and margin data are insufficient.")
                   : List.of("F/K, PD/DD, ROE, ROA ve marj verileri yeterli degil."),
                en ? List.of("Missing financial data prevents confirmation of profitability, leverage and growth outlook.")
                   : List.of("Eksik finansal veri nedeniyle karlılık, borcluluк ve buyume gorunumu teyit edilemiyor."),
                en ? "Insufficient comparable period data for growth commentary."
                   : "Buyume yorumu icin yeterli karsilastirmali donem verisi yok.",
                health,
                DISCLAIMER,
                AiResponseMetadata.deterministic("INSUFFICIENT")
        );
    }

    private String growthText(String label, BigDecimal value, String fallbackLabel, String lang) {
        boolean en = "en".equals(lang);
        if (fallbackLabel != null && !fallbackLabel.isBlank()) {
            return label + ": " + fallbackLabel + ".";
        }
        if (value == null) {
            return label + ": " + (en ? "insufficient data." : "yeterli veri yok.");
        }
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            return en
                ? label + ": positive growth supports a constructive outlook (" + formatPercent(value) + ")."
                : label + ": pozitif büyüme güçlü görünümü destekliyor (" + formatPercent(value) + ").";
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return en
                ? label + ": negative growth is a pressure/risk signal (" + formatPercent(value) + ")."
                : label + ": negatif büyüme baskı/risk sinyali veriyor (" + formatPercent(value) + ").";
        }
        return en
            ? label + ": flat growth, signalling limited momentum."
            : label + ": yatay büyüme, sınırlı ivmeye işaret ediyor.";
    }

    private boolean greaterThan(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) > 0;
    }

    private boolean lessThan(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) < 0;
    }

    private boolean lessThanOrEqual(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) <= 0;
    }

    private boolean between(BigDecimal value, String lowerExclusive, String upperInclusive) {
        return value != null
                && value.compareTo(new BigDecimal(lowerExclusive)) > 0
                && value.compareTo(new BigDecimal(upperInclusive)) <= 0;
    }

    private boolean containsTurkish(String value, String needle) {
        return value != null && value.toLowerCase(Locale.forLanguageTag("tr-TR")).contains(needle);
    }

    private String formatRatio(BigDecimal value) {
        if (value == null) return "-";
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "-";
        return value.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "-";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private AiFundamentalAnalysisResponse withCacheHit(AiFundamentalAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic("INSUFFICIENT").withCacheHit(cacheHit);
        return new AiFundamentalAnalysisResponse(
                response.symbol(),
                response.summary(),
                response.strengths(),
                response.weaknesses(),
                response.risks(),
                response.growthComment(),
                response.financialHealth(),
                response.disclaimer(),
                metadata
        );
    }

    private record LatestFinancials(BigDecimal revenue, BigDecimal netProfit) {
    }
}




