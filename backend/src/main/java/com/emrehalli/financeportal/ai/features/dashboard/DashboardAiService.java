package com.emrehalli.financeportal.ai.features.dashboard;

import com.emrehalli.financeportal.ai.features.dashboard.DashboardAiResponse.MarketTone;
import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import com.emrehalli.financeportal.ai.core.provider.AiResponse;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import com.emrehalli.financeportal.ai.core.gateway.AiGatewayService;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService;
import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.core.gateway.AiResponseLogHelper;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DashboardAiService {

    private static final Logger logger = LogManager.getLogger(DashboardAiService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(4);

    private final CurrentUserResolver currentUserResolver;
    private final NewsRepository newsRepository;
    private final DashboardAiPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponseLogHelper responseLogHelper;
    private final ObjectMapper objectMapper;

    public DashboardAiService(CurrentUserResolver currentUserResolver,
                              NewsRepository newsRepository,
                              DashboardAiPromptBuilder promptBuilder,
                              AiGatewayService aiGatewayService,
                              AiResponseCacheService cacheService,
                              AiResponseLogHelper responseLogHelper,
                              ObjectMapper objectMapper) {
        this.currentUserResolver = currentUserResolver;
        this.newsRepository = newsRepository;
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.responseLogHelper = responseLogHelper;
        this.objectMapper = objectMapper;
    }

    public DashboardAiResponse analyze(String language,
                                       BigDecimal avgMarketChange,
                                       int gainerCount,
                                       int loserCount,
                                       int totalQuotes) {
        String lang = AiPromptBuilder.normLang(language);
        String keycloakId = currentUserResolver.resolve().keycloakId();
        String cacheKey = "ai:dashboard:" + keycloakId + ":" + LocalDate.now() + ":" + lang;

        try {
            DashboardAiInputContext context = buildInputContext(avgMarketChange,
                    gainerCount, loserCount, totalQuotes);

            var lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey,
                    DashboardAiResponse.class,
                    () -> compute(context, lang)
            );

            DashboardAiResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.DASHBOARD_ANALYSIS, response.metadata());
            return response;

        } catch (Exception ex) {
            logger.warn("Dashboard AI critical failure. user={}, reason={}", keycloakId, ex.getMessage());
            DashboardAiResponse response = deterministicFallback();
            responseLogHelper.log(AiTaskType.DASHBOARD_ANALYSIS, response.metadata());
            return response;
        }
    }

    private DashboardAiInputContext buildInputContext(BigDecimal avgMarketChange,
                                                     int gainerCount,
                                                     int loserCount,
                                                     int totalQuotes) {
        // Recent news titles (last 5, non-KAP)
        List<String> newsTitles = new ArrayList<>();
        try {
            PageRequest page = PageRequest.of(0, 5, Sort.by("publishedAt").descending());
            newsTitles = newsRepository.findAll(page).stream()
                    .map(News::getTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
        } catch (Exception ex) {
            logger.warn("Dashboard AI: could not load news titles. reason={}", ex.getMessage());
        }

        return new DashboardAiInputContext(
                avgMarketChange,
                gainerCount,
                loserCount,
                totalQuotes,
                newsTitles
        );
    }

    private CachedValue<DashboardAiResponse> compute(DashboardAiInputContext context, String lang) {
        DashboardAiResponse fallback = deterministicFallback();

        String prompt = promptBuilder.build(context, lang);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.DASHBOARD_ANALYSIS, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("Dashboard AI generation unavailable.");
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        try {
            DashboardAiResponse response = parseResponse(aiResponse.get());
            logger.info("Dashboard AI computed. provider={}, fallbackUsed={}", response.metadata().providerUsed(), response.fallbackUsed());
            return new CachedValue<>(response, CACHE_TTL);
        } catch (Exception ex) {
            logger.warn("Dashboard AI parse failed. reason={}", ex.getMessage());
            return new CachedValue<>(fallback, CACHE_TTL);
        }
    }

    private DashboardAiResponse parseResponse(AiResponse aiResponse) throws Exception {
        JsonNode root = parseJson(aiResponse.content());
        String provider = aiResponse.provider().name().toLowerCase(Locale.ROOT);

        return new DashboardAiResponse(
                text(root, "marketContext", null),
                text(root, "newsContext", null),
                stringList(root, "riskSignals"),
                stringList(root, "watchPoints"),
                text(root, "finalComment", null),
                parseTone(root),
                aiResponse.fallbackUsed(),
                AiResponseMetadata.fromAiResponse(aiResponse, "COMPLETE")
        );
    }

    private DashboardAiResponse deterministicFallback() {
        return new DashboardAiResponse(
                null, null,
                List.of(),
                List.of(),
                null,
                MarketTone.NEUTRAL,
                false,
                AiResponseMetadata.deterministic("LIMITED")
        );
    }

    private DashboardAiResponse withCacheHit(DashboardAiResponse response, boolean cacheHit) {
        if (response == null) return deterministicFallback();
        AiResponseMetadata meta = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic("UNKNOWN").withCacheHit(cacheHit);
        return new DashboardAiResponse(
                response.marketContext(),
                response.newsContext(),
                response.riskSignals(),
                response.watchPoints(),
                response.finalComment(),
                response.marketTone(),
                response.fallbackUsed(),
                meta
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
            throw new IllegalArgumentException("No JSON object found in dashboard AI response");
        }
        return objectMapper.readTree(cleaned.substring(first, last + 1));
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) return fallback;
        return node.asText().trim();
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String val = item.asText("").trim();
            if (!val.isBlank()) result.add(val);
        }
        return List.copyOf(result);
    }

    private MarketTone parseTone(JsonNode root) {
        try {
            String raw = text(root, "marketTone", "NEUTRAL");
            if (raw == null) return MarketTone.NEUTRAL;
            return MarketTone.valueOf(raw.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException ignored) {
            return MarketTone.NEUTRAL;
        }
    }

}
