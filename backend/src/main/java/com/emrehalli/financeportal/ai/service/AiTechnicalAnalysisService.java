package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.RiskLevel;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.prompt.TechnicalAnalysisPromptBuilder;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.enums.TechnicalSignal;
import com.emrehalli.financeportal.technicalanalysis.enums.TrendDirection;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiTechnicalAnalysisService {

    private static final Logger logger = LogManager.getLogger(AiTechnicalAnalysisService.class);
    private static final String DISCLAIMER = "Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir; yalnÄ±zca mevcut verilerin otomatik analizidir.";
    private static final String DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofMinutes(30);

    private final TechnicalAnalysisService technicalAnalysisService;
    private final AiGenerationService aiGenerationService;
    private final AiResponseCacheService aiResponseCacheService;
    private final TechnicalAnalysisPromptBuilder promptBuilder;
    private final AiResponseLogHelper responseLogHelper;

    public AiTechnicalAnalysisService(TechnicalAnalysisService technicalAnalysisService,
                                      AiGenerationService aiGenerationService,
                                      AiResponseCacheService aiResponseCacheService,
                                      TechnicalAnalysisPromptBuilder promptBuilder,
                                      AiResponseLogHelper responseLogHelper) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.aiGenerationService = aiGenerationService;
        this.aiResponseCacheService = aiResponseCacheService;
        this.promptBuilder = promptBuilder;
        this.responseLogHelper = responseLogHelper;
    }

    public AiTechnicalAnalysisResponse getTechnicalComment(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String cacheKey = "ai:technical:" + normalizedSymbol;
        try {
            LookupResult<AiTechnicalAnalysisResponse> lookup = aiResponseCacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, AiTechnicalAnalysisResponse.class, () -> computeTechnicalComment(normalizedSymbol));
            AiTechnicalAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.TECHNICAL_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI technical endpoint critical failure, returning fallback. symbol={}, reason={}", normalizedSymbol, exception.getMessage());
            AiTechnicalAnalysisResponse response = dataLimitedFallback(normalizedSymbol);
            responseLogHelper.log(AiTaskType.TECHNICAL_ANALYSIS, response.metadata());
            return response;
        }
    }

    private CachedValue<AiTechnicalAnalysisResponse> computeTechnicalComment(String normalizedSymbol) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(180);
            TechnicalAnalysisResult analysis = technicalAnalysisService.analyze(normalizedSymbol, from, to, DEFAULT_INDICATORS);
            AiTechnicalAnalysisResponse ruleBased = fromTechnicalAnalysis(normalizedSymbol, analysis);
            AiGenerationService.EnhancedResult<AiTechnicalAnalysisResponse> enhanced =
                    aiGenerationService.enhanceTechnical(promptBuilder.build(normalizedSymbol, analysis), ruleBased);
            Duration ttl = enhanced.fromLlm() ? CACHE_TTL : FALLBACK_CACHE_TTL;
            String summarySnippet = enhanced.response().summary();
            logger.info("AI technical computed. symbol={}, source={}, provider={}, ttlMinutes={}, summaryPreview={}",
                    normalizedSymbol,
                    enhanced.fromLlm() ? "LLM_SUCCESS" : "RULE_BASED_FALLBACK",
                    enhanced.metadata() != null ? enhanced.metadata().providerUsed() : null,
                    ttl.toMinutes(),
                    summarySnippet != null && summarySnippet.length() > 100 ? summarySnippet.substring(0, 100) : summarySnippet);
            return new CachedValue<>(enhanced.response(), ttl);
        } catch (Exception exception) {
            logger.warn("AI technical analysis used data-limited fallback. symbol={}, source=RULE_BASED_FALLBACK, reason={}", normalizedSymbol, exception.getMessage());
            return new CachedValue<>(dataLimitedFallback(normalizedSymbol), FALLBACK_CACHE_TTL);
        }
    }

    private AiTechnicalAnalysisResponse fromTechnicalAnalysis(String symbol, TechnicalAnalysisResult analysis) {
        BigDecimal latestPrice = analysis.latestPrice();
        BigDecimal rsi = analysis.indicatorValues().get(IndicatorType.RSI14);
        BigDecimal sma20 = analysis.indicatorValues().get(IndicatorType.SMA20);
        BigDecimal sma50 = analysis.indicatorValues().get(IndicatorType.SMA50);
        TrendDirection trendDirection = analysis.trendDirection() != null ? analysis.trendDirection() : TrendDirection.SIDEWAYS;
        List<TechnicalSignal> signals = analysis.signals() != null ? analysis.signals() : List.of();

        RiskLevel riskLevel = resolveRiskLevel(trendDirection, rsi, signals, analysis.analysisStatus());
        AiSignal signal = resolveSignal(trendDirection, rsi, signals, riskLevel);

        String summary = buildSummary(symbol, latestPrice, rsi, sma20, sma50, trendDirection, signals, signal, analysis.analysisStatus());
        String trendComment = buildTrendComment(trendDirection, signals, latestPrice, sma20);
        String momentumComment = buildMomentumComment(rsi, trendDirection, signals);

        return new AiTechnicalAnalysisResponse(
                symbol,
                summary,
                trendComment,
                momentumComment,
                riskLevel,
                signal,
                DISCLAIMER,
                AiResponseMetadata.deterministic("FULL")
        );
    }

    private RiskLevel resolveRiskLevel(TrendDirection trendDirection, BigDecimal rsi, List<TechnicalSignal> signals, String status) {
        if (!"AVAILABLE".equals(status)) {
            return RiskLevel.MEDIUM;
        }
        if (isRsiAbove(rsi, 70) || trendDirection == TrendDirection.DOWNTREND) {
            return RiskLevel.HIGH;
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20) || isRsiBelow(rsi, 30)) {
            return RiskLevel.HIGH;
        }
        if (isRsiAbove(rsi, 65) || isRsiBelow(rsi, 35)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private AiSignal resolveSignal(TrendDirection trendDirection, BigDecimal rsi, List<TechnicalSignal> signals, RiskLevel riskLevel) {
        if (isRsiAbove(rsi, 70)) {
            return AiSignal.RISKY;
        }
        if (trendDirection == TrendDirection.DOWNTREND || signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            return AiSignal.NEGATIVE;
        }
        if (isRsiBelow(rsi, 30)) {
            return AiSignal.RISKY;
        }
        if (trendDirection == TrendDirection.UPTREND || signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            return AiSignal.POSITIVE;
        }
        return riskLevel == RiskLevel.HIGH ? AiSignal.RISKY : AiSignal.NEUTRAL;
    }

    private String buildSummary(String symbol,
                                BigDecimal latestPrice,
                                BigDecimal rsi,
                                BigDecimal sma20,
                                BigDecimal sma50,
                                TrendDirection trendDirection,
                                List<TechnicalSignal> signals,
                                AiSignal signal,
                                String status) {
        if (!"AVAILABLE".equals(status)) {
            return symbol + " iÃ§in teknik veri sÄ±nÄ±rlÄ±. Yorum, mevcut fiyat geÃ§miÅŸi yetersiz olduÄŸu iÃ§in temkinli ve nÃ¶tr deÄŸerlendirilmelidir.";
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("trend " + formatTrend(trendDirection));
        if (rsi != null) {
            reasons.add("RSI " + rsi.stripTrailingZeros().toPlainString());
        }
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            reasons.add("fiyat 20 gÃ¼nlÃ¼k ortalamanÄ±n Ã¼zerinde");
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            reasons.add("fiyat 20 gÃ¼nlÃ¼k ortalamanÄ±n altÄ±nda");
        }
        if (sma20 != null && sma50 != null) {
            reasons.add("SMA20/SMA50 iliÅŸkisi izlenebilir");
        }

        String pricePart = latestPrice == null ? "son fiyat verisiyle" : "son fiyat " + latestPrice.stripTrailingZeros().toPlainString() + " ile";
        return symbol + " " + pricePart + " deterministic teknik kurallara gÃ¶re " + signal + " sinyal Ã¼retiyor; "
                + String.join(", ", reasons) + ".";
    }

    private String buildTrendComment(TrendDirection trendDirection, List<TechnicalSignal> signals, BigDecimal latestPrice, BigDecimal sma20) {
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            return "Fiyat 20 gÃ¼nlÃ¼k ortalamanÄ±n Ã¼zerinde; kÄ±sa vadeli trend pozitif bÃ¶lgede deÄŸerlendiriliyor.";
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            return "Fiyat 20 gÃ¼nlÃ¼k ortalamanÄ±n altÄ±nda; gÃ¶rÃ¼nÃ¼m zayÄ±f ve toparlanma iÃ§in ortalama Ã¼zerine dÃ¶nÃ¼ÅŸ izlenmeli.";
        }
        if (trendDirection == TrendDirection.UPTREND) {
            return "Trend yÃ¶nÃ¼ yukarÄ±; hareketli ortalama teyidi sÄ±nÄ±rlÄ± olsa da momentum olumlu.";
        }
        if (trendDirection == TrendDirection.DOWNTREND) {
            return "Trend yÃ¶nÃ¼ aÅŸaÄŸÄ±; fiyat Ã¼zerinde baskÄ± ve risk artÄ±ÅŸÄ± var.";
        }
        if (latestPrice != null && sma20 != null) {
            return "Fiyat ile 20 gÃ¼nlÃ¼k ortalama arasÄ±ndaki iliÅŸki belirgin sinyal Ã¼retmiyor; yatay gÃ¶rÃ¼nÃ¼m Ã¶ne Ã§Ä±kÄ±yor.";
        }
        return "Trend yorumu iÃ§in hareketli ortalama verisi sÄ±nÄ±rlÄ±; gÃ¶rÃ¼nÃ¼m nÃ¶tr kabul edilmeli.";
    }

    private String buildMomentumComment(BigDecimal rsi, TrendDirection trendDirection, List<TechnicalSignal> signals) {
        if (rsi == null) {
            return "RSI verisi yok; momentum yorumu trend ve SMA sinyalleriyle sÄ±nÄ±rlÄ±.";
        }
        if (isRsiAbove(rsi, 70)) {
            return "RSI 70 Ã¼zerinde; aÅŸÄ±rÄ± alÄ±m bÃ¶lgesi nedeniyle kÄ±sa vadeli yorulma ve dÃ¼zeltme riski artÄ±yor.";
        }
        if (isRsiBelow(rsi, 30)) {
            return "RSI 30 altÄ±nda; aÅŸÄ±rÄ± satÄ±m bÃ¶lgesi tepki potansiyeli yaratsa da risk yÃ¼ksek kalÄ±yor.";
        }
        if (trendDirection == TrendDirection.UPTREND || signals.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            return "RSI nÃ¶tr bÃ¶lgede ve trend yukarÄ±; momentum olumlu ancak aÅŸÄ±rÄ± alÄ±m teyidi yok.";
        }
        if (trendDirection == TrendDirection.DOWNTREND || signals.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            return "RSI nÃ¶tr bÃ¶lgede olsa da trend zayÄ±f; momentum baskÄ± altÄ±nda.";
        }
        return "RSI nÃ¶tr bÃ¶lgede; momentum dengeli ve yÃ¶n teyidi iÃ§in yeni fiyat hareketi beklenmeli.";
    }

    private AiTechnicalAnalysisResponse dataLimitedFallback(String symbol) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        RiskLevel riskLevel = bucket == 0 ? RiskLevel.LOW : bucket == 1 ? RiskLevel.MEDIUM : RiskLevel.HIGH;
        AiSignal signal = bucket == 0 ? AiSignal.NEUTRAL : bucket == 1 ? AiSignal.NEUTRAL : AiSignal.RISKY;
        return new AiTechnicalAnalysisResponse(
                symbol,
                symbol + " iÃ§in yeterli teknik veri yok. Deterministic yorum, veri eksikliÄŸi nedeniyle yÃ¶n yerine risk ve takip ihtiyacÄ±nÄ± vurgular.",
                "SMA ve trend teyidi Ã¼retilemedi; fiyat geÃ§miÅŸi tamamlandÄ±ÄŸÄ±nda trend yorumu netleÅŸir.",
                "RSI verisi Ã¼retilemedi; momentum nÃ¶tr kabul edilmeli ve yeni veri beklenmelidir.",
                riskLevel,
                signal,
                DISCLAIMER,
                AiResponseMetadata.deterministic("LOW")
        );
    }

    private AiTechnicalAnalysisResponse withCacheHit(AiTechnicalAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic("LOW").withCacheHit(cacheHit);
        return new AiTechnicalAnalysisResponse(
                response.symbol(),
                response.summary(),
                response.trendComment(),
                response.momentumComment(),
                response.riskLevel(),
                response.signal(),
                response.disclaimer(),
                metadata
        );
    }

    private boolean isRsiAbove(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) > 0;
    }

    private boolean isRsiBelow(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) < 0;
    }

    private String formatTrend(TrendDirection trendDirection) {
        return switch (trendDirection) {
            case UPTREND -> "yukarÄ± yÃ¶nlÃ¼";
            case DOWNTREND -> "aÅŸaÄŸÄ± yÃ¶nlÃ¼";
            case SIDEWAYS -> "yatay";
        };
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "-";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}




