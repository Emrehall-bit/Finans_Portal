package com.emrehalli.financeportal.ai.features.portfolio;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.core.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.core.provider.AiResponse;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class PortfolioResponseParser {

    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;

    PortfolioResponseParser(AiResponsePostProcessor postProcessor, ObjectMapper objectMapper) {
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
    }

    PortfolioAnalysisResponse parse(AiResponse aiResponse,
                                    PortfolioAnalysisResponse fallback) throws Exception {
        JsonNode root = parseJson(aiResponse.content());
        String providerUsed = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new PortfolioAnalysisResponse(
                fallback.portfolioId(),
                fallback.portfolioName(),
                fallback.totalValue(),
                fallback.totalProfitLoss(),
                fallback.totalProfitLossPercent(),
                processText(root, "summary", fallback.summary()),
                processText(root, "allocationComment", fallback.allocationComment()),
                processText(root, "riskComment", fallback.riskComment()),
                processText(root, "diversificationComment", fallback.diversificationComment()),
                processList(root, "strongestPositions", fallback.strongestPositions()),
                processList(root, "weakestPositions", fallback.weakestPositions()),
                processList(root, "riskSignals", fallback.riskSignals()),
                processList(root, "suggestions", fallback.suggestions()),
                processText(root, "finalComment", fallback.finalComment()),
                fallback.dataQuality(),
                providerUsed,
                aiResponse.fallbackUsed(),
                AiResponseMetadata.fromAiResponse(aiResponse, fallback.dataQuality().name())
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
            throw new IllegalArgumentException("No JSON object found in AI portfolio response");
        }
        return objectMapper.readTree(cleaned.substring(first, last + 1));
    }

    private String processText(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String processed = postProcessor.process(value.trim(), AiTaskType.PORTFOLIO_ANALYSIS);
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
                String processed = postProcessor.process(text.trim(), AiTaskType.PORTFOLIO_ANALYSIS);
                if (!processed.isBlank()) {
                    values.add(processed);
                }
            }
        });
        return values.isEmpty() ? fallback : List.copyOf(values);
    }
}
