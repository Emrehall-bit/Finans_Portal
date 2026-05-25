package com.emrehalli.financeportal.ai.news;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Orchestrates AI news impact analysis:
 * 1. Loads news from NewsService (propagates 404 if not found).
 * 2. Detects category and resolves rule-based sector impacts.
 * 3. Calls LLM once (NEWS_IMPACT_ANALYSIS task) to synthesize the narrative.
 * 4. Caches the response at ai:news-impact:{newsId} for 24 h (12 h on fallback).
 */
@Service
public class NewsImpactAnalysisService {

    private static final Logger logger = LogManager.getLogger(NewsImpactAnalysisService.class);
    private static final Duration CACHE_TTL          = Duration.ofHours(24);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofHours(12);

    private final NewsService             newsService;
    private final NewsCategoryDetector    categoryDetector;
    private final SectorImpactResolver    sectorImpactResolver;
    private final NewsImpactPromptBuilder promptBuilder;
    private final AiGatewayService        aiGatewayService;
    private final AiResponseCacheService  cacheService;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper            objectMapper;
    private final AiResponseLogHelper     responseLogHelper;

    public NewsImpactAnalysisService(NewsService newsService,
                                     NewsCategoryDetector categoryDetector,
                                     SectorImpactResolver sectorImpactResolver,
                                     NewsImpactPromptBuilder promptBuilder,
                                     AiGatewayService aiGatewayService,
                                     AiResponseCacheService cacheService,
                                     AiResponsePostProcessor postProcessor,
                                     ObjectMapper objectMapper,
                                     AiResponseLogHelper responseLogHelper) {
        this.newsService          = newsService;
        this.categoryDetector     = categoryDetector;
        this.sectorImpactResolver = sectorImpactResolver;
        this.promptBuilder        = promptBuilder;
        this.aiGatewayService     = aiGatewayService;
        this.cacheService         = cacheService;
        this.postProcessor        = postProcessor;
        this.objectMapper         = objectMapper;
        this.responseLogHelper    = responseLogHelper;
    }

    public NewsImpactResponse getNewsImpactAnalysis(Long newsId) {
        // Eagerly resolve — propagates ResourceNotFoundException (→ 404) before cache lookup
        NewsResponseDto news = newsService.getNewsById(newsId);
        String cacheKey = "ai:news-impact:" + newsId;
        try {
            LookupResult<NewsImpactResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, NewsImpactResponse.class,
                    () -> compute(newsId, news));
            NewsImpactResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.NEWS_IMPACT_ANALYSIS, response.metadata());
            return response;
        } catch (Exception e) {
            logger.warn("NewsImpact AI critical failure. newsId={}, reason={}", newsId, e.getMessage());
            NewsImpactResponse response = emptyFallback(newsId);
            responseLogHelper.log(AiTaskType.NEWS_IMPACT_ANALYSIS, response.metadata());
            return response;
        }
    }

    // ── Computation ───────────────────────────────────────────────

    private CachedValue<NewsImpactResponse> compute(Long newsId, NewsResponseDto news) {
        NewsCategory category = categoryDetector.detect(news.getTitle(), news.getSummary(), news.getCategory());
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(category, news.getTitle(), news.getSummary());

        NewsImpactContext context = new NewsImpactContext(
                newsId,
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getRelatedSymbol(),
                category,
                impact.sectors(),
                impact.sentiment(),
                impact.riskLevel()
        );

        String prompt = promptBuilder.build(context);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.NEWS_IMPACT_ANALYSIS, prompt);

        if (aiResponse.isEmpty()) {
            logger.warn("NewsImpact AI: no LLM response. newsId={}", newsId);
            return new CachedValue<>(deterministicFallback(newsId, context), FALLBACK_CACHE_TTL);
        }

        try {
            CachedValue<NewsImpactResponse> result = parseResponse(newsId, aiResponse.get(), context);
            logger.info("NewsImpact AI computed. newsId={}, provider={}, fallback={}",
                    newsId, result.value().provider(), result.value().fallbackUsed());
            return result;
        } catch (Exception e) {
            logger.warn("NewsImpact AI: parse failed. newsId={}, reason={}", newsId, e.getMessage());
            return new CachedValue<>(deterministicFallback(newsId, context), FALLBACK_CACHE_TTL);
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────

    private CachedValue<NewsImpactResponse> parseResponse(Long newsId, AiResponse aiResponse,
                                                          NewsImpactContext context) throws Exception {
        String raw = aiResponse.content();
        String cleaned = raw.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last  = cleaned.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("No JSON object in news impact AI response");
        }
        JsonNode root = objectMapper.readTree(cleaned.substring(first, last + 1));

        String summary      = postProcessor.process(root.path("summary").asText(""),      AiTaskType.NEWS_IMPACT_ANALYSIS);
        String marketImpact = postProcessor.process(root.path("marketImpact").asText(""), AiTaskType.NEWS_IMPACT_ANALYSIS);
        List<String> highlights = parseList(root.path("highlights"));

        String provider = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new CachedValue<>(
                new NewsImpactResponse(
                        String.valueOf(newsId),
                        summary,
                        marketImpact,
                        context.affectedSectors(),
                        context.initialSentiment(),
                        context.initialRiskLevel(),
                        highlights,
                        provider,
                        aiResponse.fallbackUsed(),
                        AiResponseMetadata.fromAiResponse(aiResponse, "COMPLETE")
                ),
                CACHE_TTL
        );
    }

    private List<String> parseList(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return List.of();
        List<String> items = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                items.add(postProcessor.process(text.trim(), AiTaskType.NEWS_IMPACT_ANALYSIS));
            }
        });
        return List.copyOf(items);
    }

    // ── Fallbacks ─────────────────────────────────────────────────

    private NewsImpactResponse deterministicFallback(Long newsId, NewsImpactContext context) {
        String categoryLabel = context.detectedCategory().name().replace("_", " ").toLowerCase(Locale.ROOT);
        String summary = "Bu haber " + categoryLabel +
                " kategorisinde değerlendirilebilir. Piyasa etkisi için ek veri takip edilmeli.";
        return new NewsImpactResponse(
                String.valueOf(newsId),
                summary,
                "Piyasa etkisi mevcut verilerle sınırlı değerlendirilebilir.",
                context.affectedSectors(),
                context.initialSentiment(),
                context.initialRiskLevel(),
                List.of(),
                null,
                false,
                AiResponseMetadata.deterministic("PARTIAL")
        );
    }

    private NewsImpactResponse emptyFallback(Long newsId) {
        return new NewsImpactResponse(
                String.valueOf(newsId),
                "Haber etki analizi şu an hazırlanamıyor.",
                "",
                List.of(),
                "NEUTRAL",
                "LOW",
                List.of(),
                null,
                false,
                AiResponseMetadata.deterministic("LOW")
        );
    }

    private NewsImpactResponse withCacheHit(NewsImpactResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic("LOW").withCacheHit(cacheHit);
        return new NewsImpactResponse(
                response.newsId(),
                response.summary(),
                response.marketImpact(),
                response.affectedSectors(),
                response.sentiment(),
                response.riskLevel(),
                response.highlights(),
                response.provider(),
                response.fallbackUsed(),
                metadata
        );
    }
}



