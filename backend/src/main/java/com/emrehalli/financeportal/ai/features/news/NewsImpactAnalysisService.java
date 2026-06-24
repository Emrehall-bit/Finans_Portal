package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.core.gateway.AiGatewayService;
import com.emrehalli.financeportal.ai.core.gateway.AiResponseLogHelper;
import com.emrehalli.financeportal.ai.core.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.core.provider.AiResponse;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class NewsImpactAnalysisService {

    private static final Logger logger = LogManager.getLogger(NewsImpactAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofHours(12);
    private static final int MAX_LIST_ITEMS = 3;

    private final NewsService newsService;
    private final NewsCategoryDetector categoryDetector;
    private final SectorImpactResolver sectorImpactResolver;
    private final NewsImpactPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;
    private final AiResponseLogHelper responseLogHelper;

    public NewsImpactAnalysisService(NewsService newsService,
                                     NewsCategoryDetector categoryDetector,
                                     SectorImpactResolver sectorImpactResolver,
                                     NewsImpactPromptBuilder promptBuilder,
                                     AiGatewayService aiGatewayService,
                                     AiResponseCacheService cacheService,
                                     AiResponsePostProcessor postProcessor,
                                     ObjectMapper objectMapper,
                                     AiResponseLogHelper responseLogHelper) {
        this.newsService = newsService;
        this.categoryDetector = categoryDetector;
        this.sectorImpactResolver = sectorImpactResolver;
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
        this.responseLogHelper = responseLogHelper;
    }

    public NewsImpactResponse getNewsImpactAnalysis(Long newsId) {
        return getNewsImpactAnalysis(newsId, "tr");
    }

    public NewsImpactResponse getNewsImpactAnalysis(Long newsId, String language) {
        NewsResponseDto news = newsService.getNewsById(newsId);
        String lang = AiPromptBuilder.normLang(language);
        String cacheKey = "ai:news-impact:v5:" + newsId + ":" + lang;
        try {
            LookupResult<NewsImpactResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey, NewsImpactResponse.class,
                    () -> compute(newsId, news, lang));
            NewsImpactResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.NEWS_IMPACT_ANALYSIS, response.metadata());
            return response;
        } catch (Exception e) {
            logger.warn("NewsImpact AI critical failure. newsId={}, reason={}", newsId, e.getMessage());
            NewsImpactResponse response = emptyFallback(newsId, lang);
            responseLogHelper.log(AiTaskType.NEWS_IMPACT_ANALYSIS, response.metadata());
            return response;
        }
    }

    private CachedValue<NewsImpactResponse> compute(Long newsId, NewsResponseDto news, String lang) {
        NewsCategoryDetector.DetectionResult detection = categoryDetector.detectWithEvidence(news.getTitle(), news.getSummary(), news.getCategory());
        NewsCategory category = detection.category();
        SectorImpactResolver.SectorImpact impact =
                sectorImpactResolver.resolve(detection, news.getTitle(), news.getSummary());

        NewsImpactContext context = new NewsImpactContext(
                newsId,
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getRelatedSymbol(),
                category,
                detection.titleMatched(),
                detection.summaryMatched(),
                detection.categoryOnly(),
                impact.sectors()
        );

        String prompt = promptBuilder.build(context, lang);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.NEWS_IMPACT_ANALYSIS, prompt);

        if (aiResponse.isEmpty()) {
            logger.warn("NewsImpact AI: no LLM response. newsId={}", newsId);
            return new CachedValue<>(deterministicFallback(newsId, context, lang), FALLBACK_CACHE_TTL);
        }

        try {
            CachedValue<NewsImpactResponse> result = parseResponse(newsId, aiResponse.get(), context);
            logger.info("NewsImpact AI computed. newsId={}, provider={}, fallback={}",
                    newsId, result.value().provider(), result.value().fallbackUsed());
            return result;
        } catch (Exception e) {
            logger.warn("NewsImpact AI: parse failed. newsId={}, reason={}", newsId, e.getMessage());
            return new CachedValue<>(deterministicFallback(newsId, context, lang), FALLBACK_CACHE_TTL);
        }
    }

    private CachedValue<NewsImpactResponse> parseResponse(Long newsId, AiResponse aiResponse,
                                                          NewsImpactContext context) throws Exception {
        String raw = aiResponse.content();
        String cleaned = raw.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("No JSON object in news impact AI response");
        }
        JsonNode root = objectMapper.readTree(cleaned.substring(first, last + 1));

        String financialContext = processText(text(root, "financialContext"));
        String marketImplication = processText(text(root, "marketImplication"));
        String uncertainty = processText(text(root, "uncertainty"));
        String shortTermImpact = optionalImpact(root.path("shortTermImpact"));
        String mediumTermImpact = optionalImpact(root.path("mediumTermImpact"));
        List<String> affectedAssets = parseAssets(root.path("affectedAssets"), List.of());
        List<String> watchIndicators = parseList(root.path("watchIndicators"), MAX_LIST_ITEMS);
        List<String> highlights = parseList(root.path("highlights"), MAX_LIST_ITEMS);

        String provider = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new CachedValue<>(
                new NewsImpactResponse(
                        String.valueOf(newsId),
                        financialContext,
                        affectedAssets,
                        marketImplication,
                        watchIndicators,
                        uncertainty,
                        highlights,
                        shortTermImpact,
                        mediumTermImpact,
                        provider,
                        aiResponse.fallbackUsed(),
                        AiResponseMetadata.fromAiResponse(aiResponse, "COMPLETE"),
                        financialContext,
                        marketImplication,
                        affectedAssets
                ),
                CACHE_TTL
        );
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return "";
        }
        return cleanText(node.asText(""));
    }

    private String optionalImpact(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = processText(cleanText(node.asText("")));
        if (!hasText(value) || isGenericWeakImpact(value)) {
            return null;
        }
        return value;
    }

    private List<String> parseList(JsonNode node, int maxItems) {
        if (!node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            String text = processText(cleanText(item.asText(null)));
            if (hasText(text)) {
                items.add(text);
            }
            if (items.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(items);
    }

    private List<String> parseAssets(JsonNode node, List<String> fallback) {
        Set<String> items = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String asset = cleanAsset(item.asText(null));
                if (hasText(asset)) {
                    items.add(asset);
                }
                if (items.size() >= MAX_LIST_ITEMS) {
                    break;
                }
            }
        }
        if (items.isEmpty() && fallback != null) {
            for (String item : fallback) {
                String asset = cleanAsset(item);
                if (hasText(asset)) {
                    items.add(asset);
                }
                if (items.size() >= MAX_LIST_ITEMS) {
                    break;
                }
            }
        }
        return List.copyOf(items);
    }

    private String processText(String value) {
        if (!hasText(value)) {
            return "";
        }
        return postProcessor.process(value.trim(), AiTaskType.NEWS_IMPACT_ANALYSIS);
    }

    private String cleanAsset(String raw) {
        String value = cleanText(raw);
        if (!hasText(value)) {
            return "";
        }
        value = value.replace("â†’", "|").replace("->", "|");
        int separator = value.indexOf('|');
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > 60 || containsBrokenEncoding(value)) {
            return "";
        }
        return value;
    }

    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("FÃ„Â°NANSAL SEKTÃƒâ€“R", "Finansal sektör")
                .replace("GENEL PÃ„Â°YASA", "Genel piyasa")
                .replace("KRÃ„Â°PTO VARLIKLARÃ„Â±", "Kripto varlıklar")
                .replace("Ã„Â°", "Ä°")
                .replace("Ã„Â±", "Ä±")
                .replace("ÃƒÂ¶", "Ã¶")
                .replace("ÃƒÂ¼", "Ã¼")
                .replace("ÃƒÂ§", "Ã§")
                .replace("Ã…Å¸", "ÅŸ")
                .replace("Ã„Å¸", "ÄŸ")
                .replace("Ãƒâ€“", "Ã–")
                .replace("ÃƒÅ“", "Ãœ")
                .replace("Ãƒâ€¡", "Ã‡")
                .replace("Ã…Å¾", "Å")
                .replace("Ã„Å¾", "Ä")
                .replace("Ã¢â‚¬â„¢", "'")
                .replace("Ã¢â‚¬â€œ", "-")
                .replace("Ã¢â‚¬â€", "-")
                .replace("Ã¢â€ â€™", "â†’")
                .trim();
    }

    private boolean containsBrokenEncoding(String value) {
        return value.contains("Ãƒ") || value.contains("Ã„") || value.contains("Ã…") || value.contains("ï¿½");
    }

    private boolean isGenericWeakImpact(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("belirgin kısa vadeli etki")
                || lower.contains("belirgin kisa vadeli etki")
                || lower.contains("orta vadeli etki için ek gelişme")
                || lower.contains("orta vadeli etki icin ek gelisme")
                || lower.contains("saptanamıyor")
                || lower.contains("saptanamiyor");
    }

    private NewsImpactResponse deterministicFallback(Long newsId, NewsImpactContext context, String lang) {
        boolean en = "en".equals(lang);
        String categoryLabel = context.detectedCategory().name().replace("_", " ").toLowerCase(Locale.ROOT);
        String financialContext = en
                ? "This news is financially relevant through the " + categoryLabel + " theme, but the direct market channel is limited with the available information."
                : "Bu haber " + categoryLabel + " teması üzerinden finansal açıdan izlenebilir; mevcut bilgi doğrudan fiyat katalizörü üretmek için sınırlı.";
        String marketImplication = en
                ? "The implication is more about sector context and market sentiment than a direct price forecast."
                : "Piyasa yansıması doğrudan fiyat tahmininden çok sektör bağlamı ve duyarlılık etkisi taşır.";
        String uncertainty = en
                ? "The news does not provide enough concrete policy, financial, or transaction detail to isolate a direct market effect."
                : "Haber, doğrudan piyasa etkisini ayrıştıracak kadar somut politika, finansal sonuç veya işlem detayı içermiyor.";
        List<String> watchIndicators = defaultWatchIndicators(context.detectedCategory(), en);
        List<String> affectedAssets = context.titleMatched() || context.summaryMatched()
                ? parseAssets(objectMapper.createArrayNode(), context.affectedSectors())
                : List.of();
        return new NewsImpactResponse(
                String.valueOf(newsId),
                financialContext,
                affectedAssets,
                marketImplication,
                watchIndicators,
                uncertainty,
                List.of(),
                null,
                null,
                null,
                false,
                AiResponseMetadata.deterministic("PARTIAL"),
                financialContext,
                marketImplication,
                affectedAssets
        );
    }

    private NewsImpactResponse emptyFallback(Long newsId, String lang) {
        boolean en = "en".equals(lang);
        String financialContext = en
                ? "News impact analysis is temporarily unavailable."
                : "Haber etki analizi şu an hazırlanamıyor.";
        String uncertainty = en
                ? "The AI response could not be produced, so no market implication is shown."
                : "AI yanıtı üretilemediği için piyasa yansıması gösterilmiyor.";
        return new NewsImpactResponse(
                String.valueOf(newsId),
                financialContext,
                List.of(),
                "",
                List.of(),
                uncertainty,
                List.of(),
                null,
                null,
                null,
                false,
                AiResponseMetadata.deterministic("LOW"),
                financialContext,
                "",
                List.of()
        );
    }

    private List<String> defaultWatchIndicators(NewsCategory category, boolean en) {
        return switch (category) {
            case TCMB, FED, INTEREST_RATE -> en
                    ? List.of("central bank statements", "bond yields", "FX market reaction")
                    : List.of("merkez bankası açıklamaları", "tahvil faizleri", "döviz piyasası tepkisi");
            case INFLATION -> en
                    ? List.of("inflation expectations", "bond yields", "sector margin comments")
                    : List.of("enflasyon beklentileri", "tahvil faizleri", "sektör marj açıklamaları");
            case CRYPTO -> en
                    ? List.of("crypto market liquidity", "regulatory statements", "risk appetite")
                    : List.of("kripto piyasa likiditesi", "regülasyon açıklamaları", "risk iştahı");
            case BANKING, REGULATION -> en
                    ? List.of("regulatory statements", "sector company announcements", "trading volume")
                    : List.of("regülasyon açıklamaları", "sektör şirketlerinin duyuruları", "işlem hacmi");
            default -> en
                    ? List.of("related sector news flow", "trading volume", "market sentiment")
                    : List.of("ilgili sektörde haber akışı", "işlem hacmi", "piyasa duyarlılığı");
        };
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
                response.financialContext(),
                response.affectedAssets(),
                response.marketImplication(),
                response.watchIndicators(),
                response.uncertainty(),
                response.highlights(),
                response.shortTermImpact(),
                response.mediumTermImpact(),
                response.provider(),
                response.fallbackUsed(),
                metadata,
                response.summary(),
                response.marketImpact(),
                response.affectedSectors()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
