package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.prompt.FundamentalAnalysisPromptBuilder;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.company.dto.CompanyFinancialReportResponse;
import com.emrehalli.financeportal.company.dto.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.dto.FinancialValueItemResponse;
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
    private static final String DISCLAIMER = "Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.";

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
        String normalizedSymbol = normalizeSymbol(symbol);
        String cacheKey = "ai:fundamental:" + normalizedSymbol;
        try {
            LookupResult<AiFundamentalAnalysisResponse> lookup = aiResponseCacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, AiFundamentalAnalysisResponse.class, () -> computeFundamentalComment(normalizedSymbol));
            AiFundamentalAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.FUNDAMENTAL_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI fundamental endpoint critical failure, returning fallback. symbol={}, reason={}", normalizedSymbol, exception.getMessage());
            AiFundamentalAnalysisResponse response = dataLimitedFallback(normalizedSymbol);
            responseLogHelper.log(AiTaskType.FUNDAMENTAL_ANALYSIS, response.metadata());
            return response;
        }
    }

    private CachedValue<AiFundamentalAnalysisResponse> computeFundamentalComment(String normalizedSymbol) {
        try {
            CompanyFundamentalsResponse fundamentals = companyQueryService.getFundamentals(normalizedSymbol);
            if (fundamentals == null || fundamentals.getMessage() != null) {
                logger.info("AI fundamental computed. symbol={}, source=RULE_BASED_FALLBACK, reason=no-fundamentals-data, ttlMinutes={}",
                        normalizedSymbol, FALLBACK_CACHE_TTL.toMinutes());
                return new CachedValue<>(dataLimitedFallback(normalizedSymbol), FALLBACK_CACHE_TTL);
            }

            LatestFinancials latestFinancials = extractLatestFinancials(companyQueryService.getFinancials(normalizedSymbol));
            AiFundamentalAnalysisResponse ruleBased = fromFundamentals(normalizedSymbol, fundamentals, latestFinancials);
            AiGenerationService.EnhancedResult<AiFundamentalAnalysisResponse> enhanced =
                    aiGenerationService.enhanceFundamental(
                            promptBuilder.build(normalizedSymbol, fundamentals, latestFinancials.revenue(), latestFinancials.netProfit()),
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
            return new CachedValue<>(dataLimitedFallback(normalizedSymbol), FALLBACK_CACHE_TTL);
        }
    }

    private AiFundamentalAnalysisResponse fromFundamentals(String symbol,
                                                          CompanyFundamentalsResponse fundamentals,
                                                          LatestFinancials latestFinancials) {
        FinancialHealth health = resolveHealth(fundamentals, latestFinancials);
        List<String> strengths = buildStrengths(fundamentals, latestFinancials);
        List<String> weaknesses = buildWeaknesses(fundamentals, latestFinancials);
        List<String> risks = buildRisks(fundamentals, latestFinancials);
        String growthComment = buildGrowthComment(fundamentals);
        String summary = buildSummary(symbol, fundamentals, latestFinancials, health);

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

    private List<String> buildStrengths(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        List<String> strengths = new ArrayList<>();
        if (greaterThan(fundamentals.getRevenueGrowth(), "0")) {
            strengths.add("Hasılat büyümesi pozitif; satış tarafında büyüme güçlü görünüyor.");
        }
        if (greaterThan(fundamentals.getNetProfitGrowth(), "0")) {
            strengths.add("Net kâr büyümesi pozitif; kârlılık yıllık bazda destekleniyor.");
        }
        if (greaterThan(fundamentals.getRoe(), "0.15")) {
            strengths.add("ROE yüksek; özkaynak kârlılığı güçlü.");
        }
        if (greaterThan(fundamentals.getRoa(), "0.04")) {
            strengths.add("ROA pozitif ve anlamlı; varlıkların kâr üretme gücü destekleyici.");
        }
        if (greaterThan(fundamentals.getGrossMargin(), "0.15")) {
            strengths.add("Brüt marj operasyonel kârlılığı destekliyor.");
        }
        if (greaterThan(fundamentals.getNetMargin(), "0.05")) {
            strengths.add("Net marj pozitif; şirket satışlarından kâr üretebiliyor.");
        }
        if (between(fundamentals.getPeRatio(), "0", "15")) {
            strengths.add("F/K oranı mevcut kârlılığa göre makul bölgede.");
        }
        if (between(fundamentals.getPbRatio(), "0", "2")) {
            strengths.add("PD/DD oranı özkaynak değerlemesine göre aşırı pahalı sinyal vermiyor.");
        }
        if (greaterThan(latestFinancials.netProfit(), "0")) {
            strengths.add("Son raporda net kâr pozitif.");
        }
        if (strengths.isEmpty()) {
            strengths.add("Mevcut veriler güçlü bir pozitif sinyal üretmiyor; daha fazla dönem verisi izlenmeli.");
        }
        return List.copyOf(strengths);
    }

    private List<String> buildWeaknesses(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        List<String> weaknesses = new ArrayList<>();
        if (fundamentals.getPeRatio() == null) {
            weaknesses.add("F/K hesaplanamıyor; net kâr veya piyasa değeri verisi eksik olabilir.");
        } else if (greaterThan(fundamentals.getPeRatio(), "25")) {
            weaknesses.add("F/K yüksek; kâr beklentilerinin önemli kısmı fiyata yansımış olabilir.");
        }
        if (fundamentals.getPbRatio() == null) {
            weaknesses.add("PD/DD hesaplanamıyor; özkaynak veya piyasa değeri verisi eksik olabilir.");
        } else if (greaterThan(fundamentals.getPbRatio(), "3")) {
            weaknesses.add("PD/DD yüksek; defter değerine göre primli fiyatlama olabilir.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            weaknesses.add("Hasılat büyümesi negatif; satış tarafında baskı var.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            weaknesses.add("Net kâr büyümesi negatif; kârlılık ivmesi zayıflıyor.");
        }
        if (lessThanOrEqual(fundamentals.getNetMargin(), "0") || lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            weaknesses.add("Net kâr veya net marj negatif; finansal baskı sinyali oluşuyor.");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("Belirgin zayıflık sınırlı; oranlar yine de sektör ortalamasıyla karşılaştırılmalı.");
        }
        return List.copyOf(weaknesses);
    }

    private List<String> buildRisks(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        List<String> risks = new ArrayList<>();
        if (greaterThan(fundamentals.getDebtToEquity(), "2")) {
            risks.add("Borç/özkaynak oranı yüksek; finansman maliyeti ve bilanço riski artabilir.");
        }
        if (lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            risks.add("Son raporda net kâr negatif veya sıfır; kârlılık sürdürülebilirliği izlenmeli.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            risks.add("Negatif hasılat büyümesi operasyonel talep veya fiyatlama baskısına işaret edebilir.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            risks.add("Negatif net kâr büyümesi marj veya maliyet baskısı yaratabilir.");
        }
        if (fundamentals.getRevenueGrowth() == null && fundamentals.getRevenueGrowthLabel() == null) {
            risks.add("Hasılat büyümesi için yeterli karşılaştırmalı dönem verisi yok.");
        }
        if (fundamentals.getNetProfitGrowth() == null && fundamentals.getNetProfitGrowthLabel() == null) {
            risks.add("Net kâr büyümesi için yeterli karşılaştırmalı dönem verisi yok.");
        }
        if (risks.isEmpty()) {
            risks.add("Ana riskler makro koşullar, sektör döngüsü ve finansal veri güncelliğidir.");
        }
        return List.copyOf(risks);
    }

    private String buildGrowthComment(CompanyFundamentalsResponse fundamentals) {
        List<String> comments = new ArrayList<>();
        comments.add(growthText("Hasılat", fundamentals.getRevenueGrowth(), fundamentals.getRevenueGrowthLabel()));
        comments.add(growthText("Net kâr", fundamentals.getNetProfitGrowth(), fundamentals.getNetProfitGrowthLabel()));
        comments.add(growthText("Aktifler", fundamentals.getAssetGrowth(), fundamentals.getAssetGrowthLabel()));
        return String.join(" ", comments);
    }

    private String buildSummary(String symbol,
                                CompanyFundamentalsResponse fundamentals,
                                LatestFinancials latestFinancials,
                                FinancialHealth health) {
        String netProfitPhrase = latestFinancials.netProfit() == null
                ? "net kâr verisi sınırlı"
                : latestFinancials.netProfit().compareTo(BigDecimal.ZERO) < 0
                ? "son net kâr negatif"
                : "son net kâr pozitif";

        return symbol + " için rule-based finansal sağlık görünümü " + health + ". "
                + "ROE " + formatPercent(fundamentals.getRoe())
                + ", ROA " + formatPercent(fundamentals.getRoa())
                + ", F/K " + formatRatio(fundamentals.getPeRatio())
                + ", PD/DD " + formatRatio(fundamentals.getPbRatio())
                + ", borç/özkaynak " + formatRatio(fundamentals.getDebtToEquity())
                + ". " + netProfitPhrase + "; büyüme ve marjlar bu yorumu belirleyen ana girdiler.";
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

    private AiFundamentalAnalysisResponse dataLimitedFallback(String symbol) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        FinancialHealth health = bucket == 0 ? FinancialHealth.STABLE : bucket == 1 ? FinancialHealth.WATCH : FinancialHealth.RISKY;
        return new AiFundamentalAnalysisResponse(
                symbol,
                symbol + " için temel analiz verisi sınırlı. Rule-based yorum, oranlar hesaplanana kadar veri eksikliğini ana risk olarak değerlendirir.",
                List.of("Finansal veriler tamamlandığında büyüme, marj ve değerleme kuralları otomatik yorumlanabilir."),
                List.of("F/K, PD/DD, ROE, ROA ve marj verileri yeterli değil."),
                List.of("Eksik finansal veri nedeniyle kârlılık, borçluluk ve büyüme görünümü teyit edilemiyor."),
                "Büyüme yorumu için yeterli karşılaştırmalı dönem verisi yok.",
                health,
                DISCLAIMER,
                AiResponseMetadata.deterministic("INSUFFICIENT")
        );
    }

    private String growthText(String label, BigDecimal value, String fallbackLabel) {
        if (fallbackLabel != null && !fallbackLabel.isBlank()) {
            return label + ": " + fallbackLabel + ".";
        }
        if (value == null) {
            return label + ": yeterli veri yok.";
        }
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            return label + ": pozitif büyüme güçlü görünümü destekliyor (" + formatPercent(value) + ").";
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return label + ": negatif büyüme baskı/risk sinyali veriyor (" + formatPercent(value) + ").";
        }
        return label + ": yatay büyüme, sınırlı ivmeye işaret ediyor.";
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
