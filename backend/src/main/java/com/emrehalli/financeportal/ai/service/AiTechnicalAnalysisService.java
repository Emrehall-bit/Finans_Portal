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
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
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
    private static final String DISCLAIMER = "Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.";
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
            return symbol + " için teknik veri sınırlı. Yorum, mevcut fiyat geçmişi yetersiz olduğu için temkinli ve nötr değerlendirilmelidir.";
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("trend " + formatTrend(trendDirection));
        if (rsi != null) {
            reasons.add("RSI " + rsi.stripTrailingZeros().toPlainString());
        }
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            reasons.add("fiyat 20 günlük ortalamanın üzerinde");
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            reasons.add("fiyat 20 günlük ortalamanın altında");
        }
        if (sma20 != null && sma50 != null) {
            reasons.add("SMA20/SMA50 ilişkisi izlenebilir");
        }

        String pricePart = latestPrice == null ? "son fiyat verisiyle" : "son fiyat " + latestPrice.stripTrailingZeros().toPlainString() + " ile";
        return symbol + " " + pricePart + " deterministic teknik kurallara göre " + signal + " sinyal üretiyor; "
                + String.join(", ", reasons) + ".";
    }

    private String buildTrendComment(TrendDirection trendDirection, List<TechnicalSignal> signals, BigDecimal latestPrice, BigDecimal sma20) {
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            return "Fiyat 20 günlük ortalamanın üzerinde; kısa vadeli trend pozitif bölgede değerlendiriliyor.";
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            return "Fiyat 20 günlük ortalamanın altında; görünüm zayıf ve toparlanma için ortalama üzerine dönüş izlenmeli.";
        }
        if (trendDirection == TrendDirection.UPTREND) {
            return "Trend yönü yukarı; hareketli ortalama teyidi sınırlı olsa da momentum olumlu.";
        }
        if (trendDirection == TrendDirection.DOWNTREND) {
            return "Trend yönü aşağı; fiyat üzerinde baskı ve risk artışı var.";
        }
        if (latestPrice != null && sma20 != null) {
            return "Fiyat ile 20 günlük ortalama arasındaki ilişki belirgin sinyal üretmiyor; yatay görünüm öne çıkıyor.";
        }
        return "Trend yorumu için hareketli ortalama verisi sınırlı; görünüm nötr kabul edilmeli.";
    }

    private String buildMomentumComment(BigDecimal rsi, TrendDirection trendDirection, List<TechnicalSignal> signals) {
        if (rsi == null) {
            return "RSI verisi yok; momentum yorumu trend ve SMA sinyalleriyle sınırlı.";
        }
        if (isRsiAbove(rsi, 70)) {
            return "RSI 70 üzerinde; aşırı alım bölgesi nedeniyle kısa vadeli yorulma ve düzeltme riski artıyor.";
        }
        if (isRsiBelow(rsi, 30)) {
            return "RSI 30 altında; aşırı satım bölgesi tepki potansiyeli yaratsa da risk yüksek kalıyor.";
        }
        if (trendDirection == TrendDirection.UPTREND || signals.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            return "RSI nötr bölgede ve trend yukarı; momentum olumlu ancak aşırı alım teyidi yok.";
        }
        if (trendDirection == TrendDirection.DOWNTREND || signals.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            return "RSI nötr bölgede olsa da trend zayıf; momentum baskı altında.";
        }
        return "RSI nötr bölgede; momentum dengeli ve yön teyidi için yeni fiyat hareketi beklenmeli.";
    }

    private AiTechnicalAnalysisResponse dataLimitedFallback(String symbol) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        RiskLevel riskLevel = bucket == 0 ? RiskLevel.LOW : bucket == 1 ? RiskLevel.MEDIUM : RiskLevel.HIGH;
        AiSignal signal = bucket == 0 ? AiSignal.NEUTRAL : bucket == 1 ? AiSignal.NEUTRAL : AiSignal.RISKY;
        return new AiTechnicalAnalysisResponse(
                symbol,
                symbol + " için yeterli teknik veri yok. Deterministic yorum, veri eksikliği nedeniyle yön yerine risk ve takip ihtiyacını vurgular.",
                "SMA ve trend teyidi üretilemedi; fiyat geçmişi tamamlandığında trend yorumu netleşir.",
                "RSI verisi üretilemedi; momentum nötr kabul edilmeli ve yeni veri beklenmelidir.",
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
            case UPTREND -> "yukarı yönlü";
            case DOWNTREND -> "aşağı yönlü";
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



