package com.emrehalli.financeportal.ai.comparison;

import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisContext.InstrumentSnapshot;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.RiskLevel;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.company.dto.CompanyFinancialReportResponse;
import com.emrehalli.financeportal.company.dto.CompanyFundamentalsResponse;
import com.emrehalli.financeportal.company.dto.FinancialValueItemResponse;
import com.emrehalli.financeportal.company.service.CompanyQueryService;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ComparisonAnalysisService {

    private static final Logger logger = LogManager.getLogger(ComparisonAnalysisService.class);
    private static final String DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final TechnicalAnalysisService technicalAnalysisService;
    private final CompanyQueryService companyQueryService;
    private final ComparisonAnalysisPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;

    public ComparisonAnalysisService(TechnicalAnalysisService technicalAnalysisService,
                                     CompanyQueryService companyQueryService,
                                     ComparisonAnalysisPromptBuilder promptBuilder,
                                     AiGatewayService aiGatewayService,
                                     AiResponseCacheService cacheService,
                                     AiResponsePostProcessor postProcessor,
                                     ObjectMapper objectMapper) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.companyQueryService = companyQueryService;
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
    }

    public ComparisonAnalysisResponse getComparisonAnalysis(String leftSymbol, String rightSymbol) {
        String left = normalizeSymbol(leftSymbol);
        String right = normalizeSymbol(rightSymbol);
        validateSymbols(left, right);

        String normalizedPair = normalizedPair(left, right);
        String cacheKey = "ai:comparison-analysis:" + normalizedPair;

        try {
            return cacheService.getOrComputeWithDynamicTtl(
                    cacheKey,
                    ComparisonAnalysisResponse.class,
                    () -> compute(left, right)
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            logger.warn("AI comparison endpoint critical failure. pair={}, reason={}", normalizedPair, exception.getMessage());
            return deterministicFallback(buildUnavailableSnapshot(left), buildUnavailableSnapshot(right), DataQuality.LIMITED);
        }
    }

    String normalizedPair(String left, String right) {
        return List.of(normalizeSymbol(left), normalizeSymbol(right)).stream()
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + "-" + b)
                .orElse("-");
    }

    private CachedValue<ComparisonAnalysisResponse> compute(String left, String right) {
        InstrumentSnapshot leftSnapshot = buildSnapshot(left);
        InstrumentSnapshot rightSnapshot = buildSnapshot(right);
        DataQuality dataQuality = determineDataQuality(leftSnapshot, rightSnapshot);
        ComparisonAnalysisContext context = new ComparisonAnalysisContext(leftSnapshot, rightSnapshot, dataQuality);
        ComparisonAnalysisResponse fallback = deterministicFallback(leftSnapshot, rightSnapshot, dataQuality);

        String prompt = promptBuilder.build(context);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.COMPANY_COMPARISON, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("AI comparison generation unavailable. pair={}-{}", left, right);
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        try {
            ComparisonAnalysisResponse response = parseResponse(aiResponse.get(), fallback);
            logger.info("AI comparison computed. left={}, right={}, provider={}, fallbackUsed={}",
                    left, right, response.providerUsed(), response.fallbackUsed());
            return new CachedValue<>(response, CACHE_TTL);
        } catch (Exception exception) {
            logger.warn("AI comparison parse failed. left={}, right={}, reason={}", left, right, exception.getMessage());
            return new CachedValue<>(fallback, CACHE_TTL);
        }
    }

    private ComparisonAnalysisResponse parseResponse(AiResponse aiResponse,
                                                     ComparisonAnalysisResponse fallback) throws Exception {
        JsonNode root = parseJson(aiResponse.content());
        String providerUsed = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new ComparisonAnalysisResponse(
                fallback.leftSymbol(),
                fallback.rightSymbol(),
                processText(root, "summary", fallback.summary()),
                processText(root, "technicalComparison", fallback.technicalComparison()),
                processText(root, "fundamentalComparison", fallback.fundamentalComparison()),
                processText(root, "riskComparison", fallback.riskComparison()),
                processList(root, "strengthsLeft", fallback.strengthsLeft()),
                processList(root, "strengthsRight", fallback.strengthsRight()),
                processList(root, "weaknessesLeft", fallback.weaknessesLeft()),
                processList(root, "weaknessesRight", fallback.weaknessesRight()),
                processText(root, "finalComment", fallback.finalComment()),
                fallback.dataQuality(),
                providerUsed,
                aiResponse.fallbackUsed()
        );
    }

    private JsonNode parseJson(String raw) throws Exception {
        String cleaned = raw.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("No JSON object found in AI comparison response");
        }
        return objectMapper.readTree(cleaned.substring(first, last + 1));
    }

    private String processText(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String processed = postProcessor.process(value.trim(), AiTaskType.COMPANY_COMPARISON);
        return processed.isBlank() ? fallback : processed;
    }

    private List<String> processList(JsonNode root, String field, List<String> fallback) {
        JsonNode node = root.path(field);
        if (!node.isArray() || node.isEmpty()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                String processed = postProcessor.process(text.trim(), AiTaskType.COMPANY_COMPARISON);
                if (!processed.isBlank()) {
                    values.add(processed);
                }
            }
        });
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private InstrumentSnapshot buildSnapshot(String symbol) {
        TechnicalSnapshot technicalSnapshot = buildTechnicalSnapshot(symbol);
        FundamentalSnapshot fundamentalSnapshot = buildFundamentalSnapshot(symbol);
        int overallRisk = calculateOverallRiskScore(technicalSnapshot.riskLevel(), fundamentalSnapshot.financialHealth(), fundamentalSnapshot.available());

        return new InstrumentSnapshot(
                symbol,
                fundamentalSnapshot.displayName() != null ? fundamentalSnapshot.displayName() : symbol,
                technicalSnapshot.available(),
                technicalSnapshot.latestPrice(),
                technicalSnapshot.rsi(),
                technicalSnapshot.trendLabel(),
                technicalSnapshot.signal(),
                technicalSnapshot.riskLevel(),
                technicalSnapshot.strengths(),
                technicalSnapshot.weaknesses(),
                fundamentalSnapshot.available(),
                fundamentalSnapshot.financialHealth(),
                fundamentalSnapshot.strengths(),
                fundamentalSnapshot.weaknesses(),
                fundamentalSnapshot.risks(),
                overallRisk
        );
    }

    private TechnicalSnapshot buildTechnicalSnapshot(String symbol) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(180);
            TechnicalAnalysisResult analysis = technicalAnalysisService.analyze(symbol, from, to, DEFAULT_INDICATORS);
            BigDecimal rsi = analysis.indicatorValues().get(IndicatorType.RSI14);
            RiskLevel riskLevel = resolveTechnicalRisk(analysis.trendDirection(), rsi, analysis.signals(), analysis.analysisStatus());
            AiSignal signal = resolveTechnicalSignal(analysis.trendDirection(), rsi, analysis.signals(), riskLevel);
            return new TechnicalSnapshot(
                    true,
                    analysis.latestPrice(),
                    rsi,
                    formatTrend(analysis.trendDirection()),
                    signal,
                    riskLevel,
                    buildTechnicalStrengths(analysis, rsi),
                    buildTechnicalWeaknesses(analysis, rsi)
            );
        } catch (Exception exception) {
            logger.debug("AI comparison technical snapshot unavailable. symbol={}, reason={}", symbol, exception.getMessage());
            return new TechnicalSnapshot(
                    false,
                    null,
                    null,
                    "Veri sinirli",
                    AiSignal.NEUTRAL,
                    RiskLevel.MEDIUM,
                    List.of("Teknik veri sinirli; net pozitif ayrisamiyor."),
                    List.of("Trend, RSI veya hareketli ortalama verisi yetersiz.")
            );
        }
    }

    private FundamentalSnapshot buildFundamentalSnapshot(String symbol) {
        try {
            CompanyFundamentalsResponse fundamentals = companyQueryService.getFundamentals(symbol);
            if (fundamentals == null || fundamentals.getMessage() != null) {
                return FundamentalSnapshot.unavailable(symbol);
            }
            LatestFinancials latestFinancials = extractLatestFinancials(symbol);
            FinancialHealth health = resolveFinancialHealth(fundamentals, latestFinancials);
            return new FundamentalSnapshot(
                    true,
                    fundamentals.getCompanyName(),
                    health,
                    buildFundamentalStrengths(fundamentals, latestFinancials),
                    buildFundamentalWeaknesses(fundamentals, latestFinancials),
                    buildFundamentalRisks(fundamentals, latestFinancials)
            );
        } catch (ResourceNotFoundException exception) {
            logger.debug("AI comparison fundamental snapshot skipped. symbol={}, reason={}", symbol, exception.getMessage());
            return FundamentalSnapshot.unavailable(symbol);
        } catch (Exception exception) {
            logger.debug("AI comparison fundamental snapshot unavailable. symbol={}, reason={}", symbol, exception.getMessage());
            return FundamentalSnapshot.unavailable(symbol);
        }
    }

    private DataQuality determineDataQuality(InstrumentSnapshot left, InstrumentSnapshot right) {
        if (!left.technicalAvailable() || !right.technicalAvailable()) {
            return DataQuality.LIMITED;
        }
        if (left.fundamentalsAvailable() && right.fundamentalsAvailable()) {
            return DataQuality.COMPLETE;
        }
        return DataQuality.PARTIAL;
    }

    private ComparisonAnalysisResponse deterministicFallback(InstrumentSnapshot left,
                                                             InstrumentSnapshot right,
                                                             DataQuality dataQuality) {
        return new ComparisonAnalysisResponse(
                left.symbol(),
                right.symbol(),
                buildSummary(left, right, dataQuality),
                buildTechnicalComparison(left, right),
                buildFundamentalComparison(left, right),
                buildRiskComparison(left, right),
                mergeStrengths(left.technicalStrengths(), left.fundamentalStrengths()),
                mergeStrengths(right.technicalStrengths(), right.fundamentalStrengths()),
                mergeStrengths(left.technicalWeaknesses(), left.fundamentalWeaknesses()),
                mergeStrengths(right.technicalWeaknesses(), right.fundamentalWeaknesses()),
                buildFinalComment(left, right, dataQuality),
                dataQuality,
                "rule-based",
                true
        );
    }

    private String buildSummary(InstrumentSnapshot left, InstrumentSnapshot right, DataQuality dataQuality) {
        String technicalLeader = left.technicalSignal().ordinal() <= right.technicalSignal().ordinal()
                ? left.symbol()
                : right.symbol();
        String riskier = left.overallRiskScore() >= right.overallRiskScore() ? left.symbol() : right.symbol();
        return String.format(
                Locale.ROOT,
                "%s ile %s karsilastirmasinda teknik tarafta %s gorece daha dengeli dururken, risk tarafinda %s daha fazla dikkat gerektiriyor. Veri kalitesi %s seviyesinde.",
                left.symbol(),
                right.symbol(),
                technicalLeader,
                riskier,
                dataQuality.name().toLowerCase(Locale.ROOT)
        );
    }

    private String buildTechnicalComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        return left.symbol() + " teknik olarak " + describeTechnical(left)
                + "; " + right.symbol() + " ise " + describeTechnical(right)
                + ". Kisa vadeli karsilastirmada sinyal/risk dengesi birlikte okunmali.";
    }

    private String buildFundamentalComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        if (!left.fundamentalsAvailable() && !right.fundamentalsAvailable()) {
            return "Iki enstruman icin de karsilastirilabilir temel veri yok; bu katman teknik gorunumun gerisinde kaliyor.";
        }
        if (!left.fundamentalsAvailable() || !right.fundamentalsAvailable()) {
            String missing = !left.fundamentalsAvailable() ? left.symbol() : right.symbol();
            String available = !left.fundamentalsAvailable() ? right.symbol() : left.symbol();
            return available + " tarafinda temel metrikler okunabilirken, " + missing
                    + " icin ayni derinlikte temel veri yok. Bu nedenle temel karsilastirma parcali yorumlanmali.";
        }
        return left.symbol() + " finansal saglikta " + describeFinancial(left.financialHealth())
                + " profiline yakin; " + right.symbol() + " ise " + describeFinancial(right.financialHealth())
                + " gorunum sunuyor. Marj, buyume ve borcluluk dengesi birlikte degerlendirilmeli.";
    }

    private String buildRiskComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        if (left.overallRiskScore() == right.overallRiskScore()) {
            return "Toplam risk profili iki tarafta birbirine yakin. Ayrim daha cok veri kalitesi ve momentum farklarindan geliyor.";
        }
        InstrumentSnapshot riskier = left.overallRiskScore() > right.overallRiskScore() ? left : right;
        InstrumentSnapshot steadier = riskier == left ? right : left;
        return riskier.symbol() + " tarafinda teknik/fundamental riskler daha yogun gorunuyor; "
                + steadier.symbol() + " ise su an icin nispeten daha dengeli bir risk profili sunuyor.";
    }

    private String buildFinalComment(InstrumentSnapshot left, InstrumentSnapshot right, DataQuality dataQuality) {
        String balancedSide = left.overallRiskScore() <= right.overallRiskScore() ? left.symbol() : right.symbol();
        return "Premium AI karsilastirma sonucu, " + balancedSide
                + " tarafinin mevcut verilerle daha dengeli bir profil verdigini; ancak kararin zamanlama, veri kalitesi ve sektor/enstruman baglami ile birlikte okunmasi gerektigini gosteriyor. Veri kalitesi "
                + dataQuality.name().toLowerCase(Locale.ROOT) + " seviyesinde.";
    }

    private List<String> mergeStrengths(List<String> technical, List<String> fundamental) {
        List<String> merged = new ArrayList<>();
        if (technical != null) {
            merged.addAll(technical);
        }
        if (fundamental != null) {
            merged.addAll(fundamental);
        }
        return merged.isEmpty() ? List.of("Belirgin guclu yon cikmiyor.") : List.copyOf(merged.subList(0, Math.min(4, merged.size())));
    }

    private String describeTechnical(InstrumentSnapshot snapshot) {
        return snapshot.trendLabel().toLowerCase(Locale.ROOT)
                + ", sinyal " + snapshot.technicalSignal().name().toLowerCase(Locale.ROOT)
                + ", risk " + snapshot.technicalRisk().name().toLowerCase(Locale.ROOT)
                + " bir gorunume sahip";
    }

    private String describeFinancial(FinancialHealth financialHealth) {
        return switch (financialHealth) {
            case STRONG -> "gorece guclu";
            case STABLE -> "dengeli";
            case WATCH -> "izlenmesi gereken";
            case RISKY -> "kirilgan";
        };
    }

    private RiskLevel resolveTechnicalRisk(TrendDirection trendDirection,
                                           BigDecimal rsi,
                                           List<TechnicalSignal> signals,
                                           String analysisStatus) {
        if (!"AVAILABLE".equals(analysisStatus)) {
            return RiskLevel.MEDIUM;
        }
        if (isRsiAbove(rsi, 70) || trendDirection == TrendDirection.DOWNTREND) {
            return RiskLevel.HIGH;
        }
        if (signals != null && signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            return RiskLevel.HIGH;
        }
        if (isRsiBelow(rsi, 35) || isRsiAbove(rsi, 65)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private AiSignal resolveTechnicalSignal(TrendDirection trendDirection,
                                            BigDecimal rsi,
                                            List<TechnicalSignal> signals,
                                            RiskLevel riskLevel) {
        if (isRsiAbove(rsi, 70) || isRsiBelow(rsi, 30)) {
            return AiSignal.RISKY;
        }
        if (trendDirection == TrendDirection.UPTREND || contains(signals, TechnicalSignal.PRICE_ABOVE_SMA20)) {
            return AiSignal.POSITIVE;
        }
        if (trendDirection == TrendDirection.DOWNTREND || contains(signals, TechnicalSignal.PRICE_BELOW_SMA20)) {
            return AiSignal.NEGATIVE;
        }
        return riskLevel == RiskLevel.HIGH ? AiSignal.RISKY : AiSignal.NEUTRAL;
    }

    private List<String> buildTechnicalStrengths(TechnicalAnalysisResult analysis, BigDecimal rsi) {
        List<String> strengths = new ArrayList<>();
        if (analysis.trendDirection() == TrendDirection.UPTREND) {
            strengths.add("Trend yukari yonlu.");
        }
        if (contains(analysis.signals(), TechnicalSignal.PRICE_ABOVE_SMA20)) {
            strengths.add("Fiyat 20 gunluk ortalamanin uzerinde.");
        }
        if (rsi != null && rsi.compareTo(BigDecimal.valueOf(45)) >= 0 && rsi.compareTo(BigDecimal.valueOf(65)) <= 0) {
            strengths.add("RSI asiri bolgede degil, momentum daha dengeli.");
        }
        return strengths.isEmpty() ? List.of("Teknik tarafta belirgin ustunluk sinirli.") : List.copyOf(strengths);
    }

    private List<String> buildTechnicalWeaknesses(TechnicalAnalysisResult analysis, BigDecimal rsi) {
        List<String> weaknesses = new ArrayList<>();
        if (analysis.trendDirection() == TrendDirection.DOWNTREND) {
            weaknesses.add("Trend asagi yonlu.");
        }
        if (contains(analysis.signals(), TechnicalSignal.PRICE_BELOW_SMA20)) {
            weaknesses.add("Fiyat 20 gunluk ortalamanin altinda.");
        }
        if (isRsiAbove(rsi, 70)) {
            weaknesses.add("RSI asiri alim bolgesinde.");
        }
        if (isRsiBelow(rsi, 30)) {
            weaknesses.add("RSI asiri satim bolgesinde.");
        }
        return weaknesses.isEmpty() ? List.of("Teknik zayiflik belirgin degil.") : List.copyOf(weaknesses);
    }

    private FinancialHealth resolveFinancialHealth(CompanyFundamentalsResponse fundamentals,
                                                   LatestFinancials latestFinancials) {
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

        if (riskFlags >= 3) return FinancialHealth.RISKY;
        if (riskFlags >= 1 && score < 4) return FinancialHealth.WATCH;
        if (score >= 6) return FinancialHealth.STRONG;
        if (score >= 3) return FinancialHealth.STABLE;
        return FinancialHealth.WATCH;
    }

    private List<String> buildFundamentalStrengths(CompanyFundamentalsResponse fundamentals,
                                                   LatestFinancials latestFinancials) {
        List<String> strengths = new ArrayList<>();
        if (greaterThan(fundamentals.getRevenueGrowth(), "0")) {
            strengths.add("Hasilat buyumesi pozitif.");
        }
        if (greaterThan(fundamentals.getNetProfitGrowth(), "0")) {
            strengths.add("Net kar buyumesi pozitif.");
        }
        if (greaterThan(fundamentals.getRoe(), "0.15")) {
            strengths.add("ROE guclu.");
        }
        if (greaterThan(fundamentals.getNetMargin(), "0.05")) {
            strengths.add("Net marj pozitif ve destekleyici.");
        }
        if (greaterThan(latestFinancials.netProfit(), "0")) {
            strengths.add("Son raporda net kar pozitif.");
        }
        return strengths.isEmpty() ? List.of("Belirgin temel guc sinyali sinirli.") : List.copyOf(strengths);
    }

    private List<String> buildFundamentalWeaknesses(CompanyFundamentalsResponse fundamentals,
                                                    LatestFinancials latestFinancials) {
        List<String> weaknesses = new ArrayList<>();
        if (greaterThan(fundamentals.getPeRatio(), "25")) {
            weaknesses.add("F/K yuksek olabilir.");
        }
        if (greaterThan(fundamentals.getPbRatio(), "3")) {
            weaknesses.add("PD/DD primli gorunum veriyor.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            weaknesses.add("Hasilat buyumesi negatif.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            weaknesses.add("Net kar buyumesi negatif.");
        }
        if (lessThanOrEqual(fundamentals.getNetMargin(), "0") || lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            weaknesses.add("Net kar / net marj baski altinda.");
        }
        return weaknesses.isEmpty() ? List.of("Belirgin temel zayiflik sinirli.") : List.copyOf(weaknesses);
    }

    private List<String> buildFundamentalRisks(CompanyFundamentalsResponse fundamentals,
                                               LatestFinancials latestFinancials) {
        List<String> risks = new ArrayList<>();
        if (greaterThan(fundamentals.getDebtToEquity(), "2")) {
            risks.add("Borcluluk yuksek.");
        }
        if (lessThanOrEqual(latestFinancials.netProfit(), "0")) {
            risks.add("Net kar surdurulebilirligi zayiflayabilir.");
        }
        if (lessThan(fundamentals.getRevenueGrowth(), "0")) {
            risks.add("Operasyonel buyume baski altinda.");
        }
        if (lessThan(fundamentals.getNetProfitGrowth(), "0")) {
            risks.add("Karlilik ivmesi zayifliyor.");
        }
        return risks.isEmpty() ? List.of("Makro kosullar ve veri guncelligi izlenmeli.") : List.copyOf(risks);
    }

    private LatestFinancials extractLatestFinancials(String symbol) {
        List<CompanyFinancialReportResponse> reports = companyQueryService.getFinancials(symbol);
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

    private int calculateOverallRiskScore(RiskLevel technicalRisk,
                                          FinancialHealth financialHealth,
                                          boolean hasFundamentals) {
        int technicalScore = switch (technicalRisk) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
        if (!hasFundamentals) {
            return technicalScore + 1;
        }
        int financialScore = switch (financialHealth) {
            case STRONG -> 1;
            case STABLE -> 2;
            case WATCH -> 3;
            case RISKY -> 4;
        };
        return technicalScore + financialScore;
    }

    private InstrumentSnapshot buildUnavailableSnapshot(String symbol) {
        return new InstrumentSnapshot(
                symbol,
                symbol,
                false,
                null,
                null,
                "Veri sinirli",
                AiSignal.NEUTRAL,
                RiskLevel.MEDIUM,
                List.of("Veri sinirli."),
                List.of("Karsilastirma icin yeterli veri yok."),
                false,
                FinancialHealth.WATCH,
                List.of("Temel veri yok."),
                List.of("Temel karsilastirma kurulamadi."),
                List.of("Veri eksikligi ana risk."),
                3
        );
    }

    private void validateSymbols(String left, String right) {
        if (left.equals("-") || right.equals("-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both symbols are required");
        }
        if (left.equals(right)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two different symbols are required");
        }
    }

    private boolean contains(List<TechnicalSignal> signals, TechnicalSignal signal) {
        return signals != null && signals.contains(signal);
    }

    private boolean isRsiAbove(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) > 0;
    }

    private boolean isRsiBelow(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) < 0;
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

    private String formatTrend(TrendDirection trendDirection) {
        if (trendDirection == null) {
            return "Belirsiz";
        }
        return switch (trendDirection) {
            case UPTREND -> "Yukari";
            case DOWNTREND -> "Asagi";
            case SIDEWAYS -> "Yatay";
        };
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "-";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record TechnicalSnapshot(
            boolean available,
            BigDecimal latestPrice,
            BigDecimal rsi,
            String trendLabel,
            AiSignal signal,
            RiskLevel riskLevel,
            List<String> strengths,
            List<String> weaknesses
    ) {}

    private record FundamentalSnapshot(
            boolean available,
            String displayName,
            FinancialHealth financialHealth,
            List<String> strengths,
            List<String> weaknesses,
            List<String> risks
    ) {
        private static FundamentalSnapshot unavailable(String symbol) {
            return new FundamentalSnapshot(
                    false,
                    symbol,
                    FinancialHealth.WATCH,
                    List.of("Temel veri bulunmuyor."),
                    List.of("Temel karsilastirma sinirli."),
                    List.of("Eksik finansal veri ana risk.")
            );
        }
    }

    private record LatestFinancials(BigDecimal revenue, BigDecimal netProfit) {}
}
