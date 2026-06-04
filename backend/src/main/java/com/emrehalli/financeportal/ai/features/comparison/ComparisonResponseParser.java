package com.emrehalli.financeportal.ai.features.comparison;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.core.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.core.provider.AiResponse;
import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ComparisonResponseParser {

    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;

    ComparisonResponseParser(AiResponsePostProcessor postProcessor, ObjectMapper objectMapper) {
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
    }

    ComparisonAnalysisResponse parse(AiResponse aiResponse,
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
}
