package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.RiskLevel;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiGenerationService {

    private static final Logger logger = LogManager.getLogger(AiGenerationService.class);
    private static final int RESPONSE_PREVIEW_LIMIT = 200;
    private static final int SUMMARY_PREVIEW_LIMIT = 100;

    private final AiGatewayService aiGatewayService;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;

    public AiGenerationService(AiGatewayService aiGatewayService,
                               AiResponsePostProcessor postProcessor,
                               ObjectMapper objectMapper) {
        this.aiGatewayService = aiGatewayService;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Wraps the enhanced DTO with source metadata so callers can determine the correct cache TTL.
     * fromLlm=true means the LLM JSON was successfully parsed and used to populate the response.
     */
    public record EnhancedResult<T>(T response, boolean fromLlm, AiResponseMetadata metadata) {}

    public EnhancedResult<AiTechnicalAnalysisResponse> enhanceTechnical(
            String prompt, AiTechnicalAnalysisResponse fallback) {

        Optional<AiResponse> generated = aiGatewayService.generate(AiTaskType.TECHNICAL_ANALYSIS, prompt);
        if (generated.isEmpty()) {
            return new EnhancedResult<>(fallback, false, AiResponseMetadata.deterministic("LOW"));
        }

        AiResponse aiResponse = generated.get();
        String providerLabel = providerLabel(aiResponse);
        try {
            JsonNode root = parseJson(aiResponse.content());
            AiTechnicalAnalysisResponse result = new AiTechnicalAnalysisResponse(
                    fallback.symbol(),
                    postProcessor.process(textOrFallback(root, "summary", fallback.summary()), AiTaskType.TECHNICAL_ANALYSIS),
                    postProcessor.process(textOrFallback(root, "trendComment", fallback.trendComment()), AiTaskType.TECHNICAL_ANALYSIS),
                    postProcessor.process(textOrFallback(root, "momentumComment", fallback.momentumComment()), AiTaskType.TECHNICAL_ANALYSIS),
                    enumOrFallback(root, "riskLevel", RiskLevel.class, fallback.riskLevel()),
                    enumOrFallback(root, "signal", AiSignal.class, fallback.signal()),
                    fallback.disclaimer(),
                    AiResponseMetadata.fromAiResponse(aiResponse, fallback.metadata() != null ? fallback.metadata().dataQuality() : "FULL")
            );
            logger.info("LLM technical parse succeeded. provider={}, fallback={}, summaryPreview={}",
                    providerLabel, aiResponse.fallbackUsed(), summaryPreview(result.summary()));
            return new EnhancedResult<>(result, true, result.metadata());
        } catch (Exception exception) {
            logger.warn("LLM technical parse failed, rule-based activated. provider={}, reason={}, responsePreview={}",
                    providerLabel, exception.getMessage(), preview(aiResponse.content()));
            return new EnhancedResult<>(fallback, false, fallback.metadata());
        }
    }

    public EnhancedResult<AiFundamentalAnalysisResponse> enhanceFundamental(
            String prompt, AiFundamentalAnalysisResponse fallback) {

        Optional<AiResponse> generated = aiGatewayService.generate(AiTaskType.FUNDAMENTAL_ANALYSIS, prompt);
        if (generated.isEmpty()) {
            return new EnhancedResult<>(fallback, false, AiResponseMetadata.deterministic("LOW"));
        }

        AiResponse aiResponse = generated.get();
        String providerLabel = providerLabel(aiResponse);
        try {
            JsonNode root = parseJson(aiResponse.content());
            AiFundamentalAnalysisResponse result = new AiFundamentalAnalysisResponse(
                    fallback.symbol(),
                    postProcessor.process(textOrFallback(root, "summary", fallback.summary()), AiTaskType.FUNDAMENTAL_ANALYSIS),
                    processStringList(stringListOrFallback(root, "strengths", fallback.strengths()), AiTaskType.FUNDAMENTAL_ANALYSIS),
                    processStringList(stringListOrFallback(root, "weaknesses", fallback.weaknesses()), AiTaskType.FUNDAMENTAL_ANALYSIS),
                    processStringList(stringListOrFallback(root, "risks", fallback.risks()), AiTaskType.FUNDAMENTAL_ANALYSIS),
                    postProcessor.process(textOrFallback(root, "growthComment", fallback.growthComment()), AiTaskType.FUNDAMENTAL_ANALYSIS),
                    enumOrFallback(root, "financialHealth", FinancialHealth.class, fallback.financialHealth()),
                    fallback.disclaimer(),
                    AiResponseMetadata.fromAiResponse(aiResponse, fallback.metadata() != null ? fallback.metadata().dataQuality() : "FULL")
            );
            logger.info("LLM fundamental parse succeeded. provider={}, fallback={}, summaryPreview={}",
                    providerLabel, aiResponse.fallbackUsed(), summaryPreview(result.summary()));
            return new EnhancedResult<>(result, true, result.metadata());
        } catch (Exception exception) {
            logger.warn("LLM fundamental parse failed, rule-based activated. provider={}, reason={}, responsePreview={}",
                    providerLabel, exception.getMessage(), preview(aiResponse.content()));
            return new EnhancedResult<>(fallback, false, fallback.metadata());
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<String> processStringList(List<String> items, AiTaskType taskType) {
        return items.stream()
                .map(item -> postProcessor.process(item, taskType))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
    }

    private String providerLabel(AiResponse response) {
        String name = response.provider().name().toLowerCase(Locale.ROOT);
        return response.fallbackUsed() ? name + "(fallback)" : name;
    }

    private JsonNode parseJson(String generated) throws Exception {
        String cleaned = generated.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw new IllegalArgumentException("No complete JSON object found in AI response");
        }
        cleaned = cleaned.substring(firstBrace, lastBrace + 1).trim();
        return objectMapper.readTree(cleaned);
    }

    private String textOrFallback(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> stringListOrFallback(JsonNode root, String field, List<String> fallback) {
        JsonNode node = root.path(field);
        if (!node.isArray() || node.isEmpty()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
            }
        });
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private <E extends Enum<E>> E enumOrFallback(JsonNode root, String field, Class<E> enumType, E fallback) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String summaryPreview(String summary) {
        if (summary == null) return "";
        return summary.length() > SUMMARY_PREVIEW_LIMIT ? summary.substring(0, SUMMARY_PREVIEW_LIMIT) : summary;
    }

    private String preview(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= RESPONSE_PREVIEW_LIMIT ? normalized : normalized.substring(0, RESPONSE_PREVIEW_LIMIT);
    }
}




