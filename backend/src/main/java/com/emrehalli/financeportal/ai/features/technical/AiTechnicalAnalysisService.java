package com.emrehalli.financeportal.ai.features.technical;

import com.emrehalli.financeportal.ai.core.gateway.AiResponseLogHelper;

import com.emrehalli.financeportal.ai.core.gateway.AiGenerationService;

import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService;

import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse.RiskLevel;
import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.features.technical.TechnicalAnalysisPromptBuilder;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
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
        return getTechnicalComment(symbol, "tr");
    }

    public AiTechnicalAnalysisResponse getTechnicalComment(String symbol, String language) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String lang = AiPromptBuilder.normLang(language);
        String cacheKey = "ai:technical:" + normalizedSymbol + ":" + lang;
        try {
            LookupResult<AiTechnicalAnalysisResponse> lookup = aiResponseCacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, AiTechnicalAnalysisResponse.class, () -> computeTechnicalComment(normalizedSymbol, lang));
            AiTechnicalAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.TECHNICAL_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI technical endpoint critical failure, returning fallback. symbol={}, reason={}", normalizedSymbol, exception.getMessage());
            AiTechnicalAnalysisResponse response = dataLimitedFallback(normalizedSymbol, lang);
            responseLogHelper.log(AiTaskType.TECHNICAL_ANALYSIS, response.metadata());
            return response;
        }
    }

    private CachedValue<AiTechnicalAnalysisResponse> computeTechnicalComment(String normalizedSymbol, String lang) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(180);
            TechnicalAnalysisResult analysis = technicalAnalysisService.analyze(normalizedSymbol, from, to, DEFAULT_INDICATORS);
            AiTechnicalAnalysisResponse ruleBased = fromTechnicalAnalysis(normalizedSymbol, analysis, lang);
            AiGenerationService.EnhancedResult<AiTechnicalAnalysisResponse> enhanced =
                    aiGenerationService.enhanceTechnical(promptBuilder.build(normalizedSymbol, analysis, lang), ruleBased);
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
            return new CachedValue<>(dataLimitedFallback(normalizedSymbol, lang), FALLBACK_CACHE_TTL);
        }
    }

    private AiTechnicalAnalysisResponse fromTechnicalAnalysis(String symbol, TechnicalAnalysisResult analysis, String lang) {
        BigDecimal latestPrice = analysis.latestPrice();
        BigDecimal rsi = analysis.indicatorValues().get(IndicatorType.RSI14);
        BigDecimal sma20 = analysis.indicatorValues().get(IndicatorType.SMA20);
        BigDecimal sma50 = analysis.indicatorValues().get(IndicatorType.SMA50);
        TrendDirection trendDirection = analysis.trendDirection() != null ? analysis.trendDirection() : TrendDirection.SIDEWAYS;
        List<TechnicalSignal> signals = analysis.signals() != null ? analysis.signals() : List.of();

        RiskLevel riskLevel = resolveRiskLevel(trendDirection, rsi, signals, analysis.analysisStatus());
        AiSignal signal = resolveSignal(trendDirection, rsi, signals, riskLevel);

        String summary = buildSummary(symbol, trendDirection, rsi, signals, signal, analysis.analysisStatus(), lang);
        String trendComment = buildTrendComment(trendDirection, signals, latestPrice, sma20, lang);
        String momentumComment = buildMomentumComment(rsi, trendDirection, signals, lang);

        return new AiTechnicalAnalysisResponse(
                symbol,
                summary,
                trendComment,
                momentumComment,
                riskLevel,
                signal,
                DISCLAIMER,
                AiResponseMetadata.deterministic("FULL"),
                null
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
                                TrendDirection trendDirection,
                                BigDecimal rsi,
                                List<TechnicalSignal> signals,
                                AiSignal signal,
                                String status,
                                String lang) {
        boolean en = "en".equals(lang);
        if (!"AVAILABLE".equals(status)) {
            return en
                ? symbol + ": technical data is limited; assessment is neutral pending sufficient price history."
                : symbol + " için teknik veri sınırlı; yorum, mevcut fiyat geçmişi yetersiz olduğu için temkinli ve nötr değerlendirilmelidir.";
        }

        String signalContext = en
            ? switch (signal) {
                case POSITIVE -> "short-term technical outlook is positive";
                case NEGATIVE -> "short-term technical pressure continues";
                case RISKY    -> "extreme zone signal detected; cautious approach warranted";
                case NEUTRAL  -> "technical direction unclear, no decisive signal";
              }
            : switch (signal) {
                case POSITIVE -> "kısa vadeli teknik görünüm olumlu";
                case NEGATIVE -> "kısa vadeli teknik baskı devam ediyor";
                case RISKY    -> "aşırı bölge sinyali mevcut; temkinli yaklaşım önerilir";
                case NEUTRAL  -> "teknik yön belirsiz, net sinyal üretilemiyor";
              };

        List<String> context = new ArrayList<>();
        if (trendDirection != null) context.add((en ? "trend " : "trend ") + formatTrend(trendDirection, lang));
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20))
            context.add(en ? "price above short-term average" : "fiyat kısa vadeli ortalama üzerinde");
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20))
            context.add(en ? "price below short-term average" : "fiyat kısa vadeli ortalama altında");
        if (isRsiAbove(rsi, 70))
            context.add(en ? "RSI in overbought zone" : "RSI aşırı alım bölgesinde");
        if (isRsiBelow(rsi, 30))
            context.add(en ? "RSI in oversold zone" : "RSI aşırı satım bölgesinde");

        return en
            ? symbol + ": " + signalContext + (context.isEmpty() ? "." : "; " + String.join(", ", context) + ".")
            : symbol + " için " + signalContext + (context.isEmpty() ? "." : "; " + String.join(", ", context) + ".");
    }

    private String buildTrendComment(TrendDirection trendDirection, List<TechnicalSignal> signals,
                                     BigDecimal latestPrice, BigDecimal sma20, String lang) {
        boolean en = "en".equals(lang);
        if (signals.contains(TechnicalSignal.PRICE_ABOVE_SMA20)) {
            return en
                ? "Price is trading above the 20-day moving average; short-term trend is in positive territory."
                : "Fiyat 20 günlük ortalamanın üzerinde; kısa vadeli trend pozitif bölgede değerlendiriliyor.";
        }
        if (signals.contains(TechnicalSignal.PRICE_BELOW_SMA20)) {
            return en
                ? "Price is below the 20-day moving average; outlook is weak — a recovery above the average should be monitored."
                : "Fiyat 20 günlük ortalamanın altında; görünüm zayıf ve toparlanma için ortalama üzerine dönüş izlenmeli.";
        }
        if (trendDirection == TrendDirection.UPTREND) {
            return en
                ? "Trend direction is up; momentum is positive despite limited moving average confirmation."
                : "Trend yönü yukarı; hareketli ortalama teyidi sınırlı olsa da momentum olumlu.";
        }
        if (trendDirection == TrendDirection.DOWNTREND) {
            return en
                ? "Trend direction is down; downward price pressure and elevated risk."
                : "Trend yönü aşağı; fiyat üzerinde baskı ve risk artışı var.";
        }
        if (latestPrice != null && sma20 != null) {
            return en
                ? "Price and 20-day moving average are not generating a clear signal; sideways movement dominates."
                : "Fiyat ile 20 günlük ortalama arasındaki ilişki belirgin sinyal üretmiyor; yatay görünüm öne çıkıyor.";
        }
        return en
            ? "Moving average data is limited for trend assessment; outlook should be treated as neutral."
            : "Trend yorumu için hareketli ortalama verisi sınırlı; görünüm nötr kabul edilmeli.";
    }

    private String buildMomentumComment(BigDecimal rsi, TrendDirection trendDirection,
                                        List<TechnicalSignal> signals, String lang) {
        boolean en = "en".equals(lang);
        if (rsi == null) {
            return en
                ? "RSI data unavailable; momentum assessment is limited to trend and SMA signals."
                : "RSI verisi yok; momentum yorumu trend ve SMA sinyalleriyle sınırlı.";
        }
        if (isRsiAbove(rsi, 70)) {
            return en
                ? "RSI above 70; elevated overbought zone — short-term exhaustion and correction risk is increasing."
                : "RSI 70 üzerinde; aşırı alım bölgesi nedeniyle kısa vadeli yorulma ve düzeltme riski artıyor.";
        }
        if (isRsiBelow(rsi, 30)) {
            return en
                ? "RSI below 30; oversold zone may create a bounce opportunity, but risk remains elevated."
                : "RSI 30 altında; aşırı satım bölgesi tepki potansiyeli yaratsa da risk yüksek kalıyor.";
        }
        if (trendDirection == TrendDirection.UPTREND || signals.contains(TechnicalSignal.SMA7_ABOVE_SMA20)) {
            return en
                ? "RSI is in neutral territory with an upward trend; momentum is positive without an overbought signal."
                : "RSI nötr bölgede ve trend yukarı; momentum olumlu ancak aşırı alım teyidi yok.";
        }
        if (trendDirection == TrendDirection.DOWNTREND || signals.contains(TechnicalSignal.SMA7_BELOW_SMA20)) {
            return en
                ? "RSI is neutral but trend is weak; momentum is under pressure."
                : "RSI nötr bölgede olsa da trend zayıf; momentum baskı altında.";
        }
        return en
            ? "RSI is in neutral territory; momentum is balanced — new price action needed to confirm direction."
            : "RSI nötr bölgede; momentum dengeli ve yön teyidi için yeni fiyat hareketi beklenmeli.";
    }

    private AiTechnicalAnalysisResponse dataLimitedFallback(String symbol, String lang) {
        int bucket = Math.abs(symbol.hashCode()) % 3;
        RiskLevel riskLevel = bucket == 0 ? RiskLevel.LOW : bucket == 1 ? RiskLevel.MEDIUM : RiskLevel.HIGH;
        AiSignal signal = bucket == 0 ? AiSignal.NEUTRAL : bucket == 1 ? AiSignal.NEUTRAL : AiSignal.RISKY;
        boolean en = "en".equals(lang);
        return new AiTechnicalAnalysisResponse(
                symbol,
                en ? symbol + ": insufficient technical data available; assessment is neutral pending more price history."
                   : symbol + " için yeterli teknik veri yok; veri eksikliği nedeniyle yön yerine risk ve takip ihtiyacı vurgulanıyor.",
                en ? "SMA and trend confirmation unavailable; trend assessment will improve as price history accumulates."
                   : "SMA ve trend teyidi üretilemedi; fiyat geçmişi tamamlandığında trend yorumu netleşir.",
                en ? "RSI data unavailable; momentum is treated as neutral pending new price data."
                   : "RSI verisi üretilemedi; momentum nötr kabul edilmeli ve yeni veri beklenmelidir.",
                riskLevel,
                signal,
                DISCLAIMER,
                AiResponseMetadata.deterministic("LOW"),
                null
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
                metadata,
                response.keyObservation()
        );
    }

    private boolean isRsiAbove(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) > 0;
    }

    private boolean isRsiBelow(BigDecimal rsi, int threshold) {
        return rsi != null && rsi.compareTo(BigDecimal.valueOf(threshold)) < 0;
    }

    private String formatTrend(TrendDirection trendDirection, String lang) {
        boolean en = "en".equals(lang);
        return switch (trendDirection) {
            case UPTREND   -> en ? "upward" : "yukarı yönlü";
            case DOWNTREND -> en ? "downward" : "aşağı yönlü";
            case SIDEWAYS  -> en ? "sideways" : "yatay";
        };
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "-";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
