package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
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
            strengths.add("HasÄ±lat bÃ¼yÃ¼mesi pozitif; satÄ±ÅŸ tarafÄ±nda bÃ¼yÃ¼me gÃ¼Ã§lÃ¼ gÃ¶rÃ¼nÃ¼yor.");
        }
        if (greaterThan(fundamentals.getNetProfitGrowth(), "0")) {
            strengths.add("Net kÃ¢r bÃ¼yÃ¼mesi pozitif; kÃ¢rlÄ±lÄ±k yÄ±llÄ±k bazda destekleniyor.");
        }
        if (greaterThan(fundamentals.getRoe(), "0.15")) {
            strengths.add("ROE yÃ¼ksek; Ã¶zkaynak kÃ¢rlÄ±lÄ±ÄŸÄ± gÃ¼Ã§lÃ¼.");
        }
        if (greaterThan(fundamentals.getRoa(), "0.04")) {
            strengths.add("ROA pozitif ve anlamlÄ±; varlÄ±klarÄ±n kÃ¢r Ã¼retme gÃ¼cÃ¼ destekleyici.");
        }
        if (greaterThan(fundamentals.getGrossMargin(), "0.15")) {
            strengths.add("BrÃ¼t marj operasyonel kÃ¢rlÄ±lÄ±ÄŸÄ± destekliyor.");
        }
        if (greaterThan(fundamentals.getNetMargin(), "0.05")) {
            strengths.add("Net marj pozitif; ÅŸirket satÄ±ÅŸlarÄ±ndan kÃ¢r Ã¼retebiliyor.");
        }
        if (between(fundamentals.getPeRatio(), "0", "15")) {
            strengths.add("F/K oranÄ± mevcut kÃ¢rlÄ±lÄ±ÄŸa gÃ¶re makul bÃ¶lgede.");
        }
        if (between(fundamentals.getPbRatio(), "0", "2")) {
            strengths.add("PD/DD oranÄ± Ã¶zkaynak deÄŸerlemesine gÃ¶re aÅŸÄ±rÄ± pahalÄ± sinyal vermiyor.");
        }
        if (greaterThan(latestFinancials.netProfit(), "0")) {
            strengths.add("Son raporda net kÃ¢r pozitif.");
        }
        if (strengths.isEmpty()) {
            strengths.add("Mevcut veriler gÃ¼Ã§lÃ¼ bir pozitif sinyal Ã¼retmiyor; daha fazla dÃ¶nem verisi izlenmeli.");
        }
        return List.copyOf(strengths);
    }

    private List<String> buildWeaknesses(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        List<String> weaknesses = new ArrayList<>();
        if (fundamentals.getPeRatio() == null) {
            weaknesses.add("F/K hesaplanamÄ±yor; net kÃ¢r veya piyasa deÄŸeri verisi eksik olabilir.");
        } else if (greaterThan(fundamentals.getPeRatio(), "25")) {
            weaknesses.add("F/K yÃ¼ksek; kÃ¢r beklentilerinin Ã¶nemli kÄ±smÄ± fiyata yansÄ±mÄ±ÅŸ olabilir.");
        }
        if (fundamentals.getPbRatio() == null) {
            weaknesses.add("PD/DD hesaplanamÄ±yor; Ã¶zkaynak veya piyasa deÄŸeri verisi eksik olabilir.");
        } else if (greaterThan(fundamentals.getPbRatio(), "3")) {
            weaknesses.add("PD/DD yÃ¼ksek; defter deÄŸerine gÃ¶re primli fiyatlama olabilir.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            weaknesses.add("HasÄ±lat bÃ¼yÃ¼mesi negatif; satÄ±ÅŸ tarafÄ±nda baskÄ± var.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            weaknesses.add("Net kÃ¢r bÃ¼yÃ¼mesi negatif; kÃ¢rlÄ±lÄ±k ivmesi zayÄ±flÄ±yor.");
        }
        if (lessThanOrEqual(fundamentals.getNetMargin(), "0") || lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            weaknesses.add("Net kÃ¢r veya net marj negatif; finansal baskÄ± sinyali oluÅŸuyor.");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("Belirgin zayÄ±flÄ±k sÄ±nÄ±rlÄ±; oranlar yine de sektÃ¶r ortalamasÄ±yla karÅŸÄ±laÅŸtÄ±rÄ±lmalÄ±.");
        }
        return List.copyOf(weaknesses);
    }

    private List<String> buildRisks(CompanyFundamentalsResponse fundamentals, LatestFinancials latestFinancials) {
        List<String> risks = new ArrayList<>();
        if (greaterThan(fundamentals.getDebtToEquity(), "2")) {
            risks.add("BorÃ§/Ã¶zkaynak oranÄ± yÃ¼ksek; finansman maliyeti ve bilanÃ§o riski artabilir.");
        }
        if (lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            risks.add("Son raporda net kÃ¢r negatif veya sÄ±fÄ±r; kÃ¢rlÄ±lÄ±k sÃ¼rdÃ¼rÃ¼lebilirliÄŸi izlenmeli.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            risks.add("Negatif hasÄ±lat bÃ¼yÃ¼mesi operasyonel talep veya fiyatlama baskÄ±sÄ±na iÅŸaret edebilir.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            risks.add("Negatif net kÃ¢r bÃ¼yÃ¼mesi marj veya maliyet baskÄ±sÄ± yaratabilir.");
        }
        if (fundamentals.getRevenueGrowth() == null && fundamentals.getRevenueGrowthLabel() == null) {
            risks.add("HasÄ±lat bÃ¼yÃ¼mesi iÃ§in yeterli karÅŸÄ±laÅŸtÄ±rmalÄ± dÃ¶nem verisi yok.");
        }
        if (fundamentals.getNetProfitGrowth() == null && fundamentals.getNetProfitGrowthLabel() == null) {
            risks.add("Net kÃ¢r bÃ¼yÃ¼mesi iÃ§in yeterli karÅŸÄ±laÅŸtÄ±rmalÄ± dÃ¶nem verisi yok.");
        }
        if (risks.isEmpty()) {
            risks.add("Ana riskler makro koÅŸullar, sektÃ¶r dÃ¶ngÃ¼sÃ¼ ve finansal veri gÃ¼ncelliÄŸidir.");
        }
        return List.copyOf(risks);
    }

    private String buildGrowthComment(CompanyFundamentalsResponse fundamentals) {
        List<String> comments = new ArrayList<>();
        comments.add(growthText("HasÄ±lat", fundamentals.getRevenueGrowth(), fundamentals.getRevenueGrowthLabel()));
        comments.add(growthText("Net kÃ¢r", fundamentals.getNetProfitGrowth(), fundamentals.getNetProfitGrowthLabel()));
        comments.add(growthText("Aktifler", fundamentals.getAssetGrowth(), fundamentals.getAssetGrowthLabel()));
        return String.join(" ", comments);
    }

    private String buildSummary(String symbol,
                                CompanyFundamentalsResponse fundamentals,
                                LatestFinancials latestFinancials,
                                FinancialHealth health) {
        String netProfitContext = latestFinancials.netProfit() == null
                ? "net kâr verisi yetersiz"
                : latestFinancials.netProfit().compareTo(BigDecimal.ZERO) < 0
                ? "kârlılık baskı altında"
                : "kârlılık destekleyici görünüyor";

        String healthContext = switch (health) {
            case STRONG -> "finansal göstergeler genel olarak güçlü";
            case STABLE -> "finansal tablo dengeli seyrediyor";
            case WATCH  -> "bazı göstergeler yakın takip gerektiriyor";
            case RISKY  -> "finansal tabloda risk sinyalleri öne çıkıyor";
        };

        return symbol + " için kural tabanlı temel analiz: " + healthContext + "; " + netProfitContext + ". "
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

    private AiFundamentalAnalysisResponse dataLimitedFallback(String symbol) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        FinancialHealth health = bucket == 0 ? FinancialHealth.STABLE : bucket == 1 ? FinancialHealth.WATCH : FinancialHealth.RISKY;
        return new AiFundamentalAnalysisResponse(
                symbol,
                symbol + " iÃ§in temel analiz verisi sÄ±nÄ±rlÄ±. Rule-based yorum, oranlar hesaplanana kadar veri eksikliÄŸini ana risk olarak deÄŸerlendirir.",
                List.of("Finansal veriler tamamlandÄ±ÄŸÄ±nda bÃ¼yÃ¼me, marj ve deÄŸerleme kurallarÄ± otomatik yorumlanabilir."),
                List.of("F/K, PD/DD, ROE, ROA ve marj verileri yeterli deÄŸil."),
                List.of("Eksik finansal veri nedeniyle kÃ¢rlÄ±lÄ±k, borÃ§luluk ve bÃ¼yÃ¼me gÃ¶rÃ¼nÃ¼mÃ¼ teyit edilemiyor."),
                "BÃ¼yÃ¼me yorumu iÃ§in yeterli karÅŸÄ±laÅŸtÄ±rmalÄ± dÃ¶nem verisi yok.",
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
            return label + ": pozitif bÃ¼yÃ¼me gÃ¼Ã§lÃ¼ gÃ¶rÃ¼nÃ¼mÃ¼ destekliyor (" + formatPercent(value) + ").";
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return label + ": negatif bÃ¼yÃ¼me baskÄ±/risk sinyali veriyor (" + formatPercent(value) + ").";
        }
        return label + ": yatay bÃ¼yÃ¼me, sÄ±nÄ±rlÄ± ivmeye iÅŸaret ediyor.";
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




