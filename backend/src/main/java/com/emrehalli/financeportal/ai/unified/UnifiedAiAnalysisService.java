package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiFundamentalAnalysisService;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.ai.service.AiTechnicalAnalysisService;
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
 * Orchestrates unified AI analysis by:
 * 1. Fetching cached technical + fundamental responses (no extra LLM call for these).
 * 2. Assembling a {@link UnifiedAnalysisContext} with pre-interpreted insight strings.
 * 3. Calling the LLM once (PAGE_ANALYSIS task) to synthesize the unified result.
 * 4. Caching the final response at ai:unified:{symbol} for 12 h (30 min on fallback).
 */
@Service
public class UnifiedAiAnalysisService {

    private static final Logger logger = LogManager.getLogger(UnifiedAiAnalysisService.class);
    private static final Duration CACHE_TTL          = Duration.ofHours(12);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofMinutes(30);

    private final AiTechnicalAnalysisService  technicalService;
    private final AiFundamentalAnalysisService fundamentalService;
    private final UnifiedInsightAssembler      assembler;
    private final UnifiedAnalysisPromptBuilder promptBuilder;
    private final AiGatewayService             aiGatewayService;
    private final AiResponseCacheService       cacheService;
    private final AiResponsePostProcessor      postProcessor;
    private final ObjectMapper                 objectMapper;
    private final AiResponseLogHelper          responseLogHelper;

    public UnifiedAiAnalysisService(AiTechnicalAnalysisService technicalService,
                                    AiFundamentalAnalysisService fundamentalService,
                                    UnifiedInsightAssembler assembler,
                                    UnifiedAnalysisPromptBuilder promptBuilder,
                                    AiGatewayService aiGatewayService,
                                    AiResponseCacheService cacheService,
                                    AiResponsePostProcessor postProcessor,
                                    ObjectMapper objectMapper,
                                    AiResponseLogHelper responseLogHelper) {
        this.technicalService   = technicalService;
        this.fundamentalService = fundamentalService;
        this.assembler          = assembler;
        this.promptBuilder      = promptBuilder;
        this.aiGatewayService   = aiGatewayService;
        this.cacheService       = cacheService;
        this.postProcessor      = postProcessor;
        this.objectMapper       = objectMapper;
        this.responseLogHelper  = responseLogHelper;
    }

    public UnifiedAnalysisResponse getUnifiedAnalysis(String symbol, String instrumentType) {
        String key = "ai:unified:" + normalize(symbol);
        try {
            LookupResult<UnifiedAnalysisResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    key, UnifiedAnalysisResponse.class,
                    () -> compute(normalize(symbol), instrumentType));
            UnifiedAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.PAGE_ANALYSIS, response.metadata());
            return response;
        } catch (Exception e) {
            logger.warn("Unified AI endpoint critical failure. symbol={}, reason={}", symbol, e.getMessage());
            UnifiedAnalysisResponse response = emptyFallback(normalize(symbol));
            responseLogHelper.log(AiTaskType.PAGE_ANALYSIS, response.metadata());
            return response;
        }
    }

    // ── Computation ───────────────────────────────────────────────

    private CachedValue<UnifiedAnalysisResponse> compute(String symbol, String instrumentType) {
        AiTechnicalAnalysisResponse technical;
        try {
            technical = technicalService.getTechnicalComment(symbol);
        } catch (Exception e) {
            logger.warn("Unified AI: technical fetch failed. symbol={}, reason={}", symbol, e.getMessage());
            return new CachedValue<>(emptyFallback(symbol), FALLBACK_CACHE_TTL);
        }

        AiFundamentalAnalysisResponse fundamental = null;
        if ("STOCK".equalsIgnoreCase(instrumentType)) {
            try {
                fundamental = fundamentalService.getFundamentalComment(symbol);
            } catch (Exception e) {
                logger.debug("Unified AI: fundamental fetch skipped (non-critical). symbol={}", symbol);
            }
        }

        UnifiedAnalysisContext context = assembler.assemble(symbol, technical, fundamental);
        String prompt = promptBuilder.build(context);

        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.PAGE_ANALYSIS, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("Unified AI: no LLM response. symbol={}", symbol);
            return new CachedValue<>(deterministicFallback(symbol, context), FALLBACK_CACHE_TTL);
        }

        try {
            CachedValue<UnifiedAnalysisResponse> result = parseResponse(symbol, aiResponse.get());
            logger.info("Unified AI computed. symbol={}, provider={}, fallback={}",
                    symbol, result.value().provider(), result.value().fallbackUsed());
            return result;
        } catch (Exception e) {
            logger.warn("Unified AI: parse failed. symbol={}, reason={}", symbol, e.getMessage());
            return new CachedValue<>(deterministicFallback(symbol, context), FALLBACK_CACHE_TTL);
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────

    private CachedValue<UnifiedAnalysisResponse> parseResponse(String symbol, AiResponse aiResponse)
            throws Exception {
        String raw = aiResponse.content();
        String cleaned = raw.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last  = cleaned.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("No JSON object in unified AI response");
        }
        JsonNode root = objectMapper.readTree(cleaned.substring(first, last + 1));

        String summary    = postProcessor.process(root.path("summary").asText(""), AiTaskType.PAGE_ANALYSIS);
        List<String> highlights = parseList(root.path("highlights"));
        List<String> risks      = parseList(root.path("risks"));

        String provider = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new CachedValue<>(
                new UnifiedAnalysisResponse(
                        symbol,
                        summary,
                        highlights,
                        risks,
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
                items.add(postProcessor.process(text.trim(), AiTaskType.PAGE_ANALYSIS));
            }
        });
        return List.copyOf(items);
    }

    // ── Fallbacks ─────────────────────────────────────────────────

    private UnifiedAnalysisResponse deterministicFallback(String symbol, UnifiedAnalysisContext ctx) {
        String summary = postProcessor.process(
                ctx.conflictNote() + " " + (ctx.technicalSummary() != null ? ctx.technicalSummary() : ""),
                AiTaskType.PAGE_ANALYSIS
        ).trim();
        List<String> risks = ctx.fundamentalRisks().isEmpty()
                ? List.of()
                : ctx.fundamentalRisks().subList(0, Math.min(2, ctx.fundamentalRisks().size()));
        return new UnifiedAnalysisResponse(symbol, summary, List.of(), risks, null, false, AiResponseMetadata.deterministic("PARTIAL"));
    }

    private UnifiedAnalysisResponse emptyFallback(String symbol) {
        return new UnifiedAnalysisResponse(
                symbol,
                "Birleşik AI analizi şu an hazırlanamıyor; teknik ve temel analiz kartlarını ayrı inceleyebilirsiniz.",
                List.of(),
                List.of(),
                null,
                false,
                AiResponseMetadata.deterministic("LOW")
        );
    }

    private UnifiedAnalysisResponse withCacheHit(UnifiedAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic("LOW").withCacheHit(cacheHit);
        return new UnifiedAnalysisResponse(
                response.symbol(),
                response.summary(),
                response.highlights(),
                response.risks(),
                response.provider(),
                response.fallbackUsed(),
                metadata
        );
    }

    private String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) return "-";
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
