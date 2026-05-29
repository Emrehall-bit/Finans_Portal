package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeminiAiClient implements LlmClient {

    private static final Logger logger = LogManager.getLogger(GeminiAiClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";
    private static final int RESPONSE_PREVIEW_LIMIT = 200;
    private static final int MAX_RETRY_COUNT = 1;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>> inFlightRequests = new ConcurrentHashMap<>();

    public GeminiAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        AiProperties.ProviderProperties gemini = properties.getGemini();
        return gemini.isEnabled()
                && gemini.getApiKey() != null && !gemini.getApiKey().isBlank()
                && gemini.getModel() != null && !gemini.getModel().isBlank();
    }

    @Override
    public Optional<String> generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        String model = properties.getGemini().getModel();
        String requestKey = "gemini:" + prompt.length() + ":" + prompt.hashCode();
        CompletableFuture<Optional<String>> future = inFlightRequests.computeIfAbsent(requestKey, key ->
                CompletableFuture.supplyAsync(() -> generateWithModel(prompt, model))
        );
        try {
            return future.join();
        } catch (CompletionException exception) {
            logger.warn("Gemini single-flight request failed. model={}, reason={}", model, exception.getMessage());
            return Optional.empty();
        } finally {
            inFlightRequests.remove(requestKey, future);
        }
    }

    private Optional<String> generateWithModel(String prompt, String model) {
        Map<String, Object> payload = requestPayload(prompt);
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            GeminiAttempt result = executeRequest(prompt, model, payload, attempt);
            if (result.text().isPresent()) {
                return result.text();
            }
            if (!result.retryable() || attempt == MAX_RETRY_COUNT) {
                return Optional.empty();
            }
            sleepBeforeRetry(model, attempt + 1);
        }
        return Optional.empty();
    }

    private GeminiAttempt executeRequest(String prompt, String model, Map<String, Object> payload, int attempt) {
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .build();

            return client.post()
                    .uri(BASE_URL, model, properties.getGemini().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        int statusCode = response.getStatusCode().value();
                        String rawResponse = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);

                        if (!response.getStatusCode().is2xxSuccessful()) {
                            boolean retryable = isRetryableStatus(statusCode);
                            if (isRateLimitResponse(statusCode, rawResponse)) {
                                logger.warn("Gemini rate limited. model={}, status={}, attempt={}, retryable={}",
                                        model, statusCode, attempt, retryable);
                            } else if (isServerErrorStatus(statusCode)) {
                                logger.warn("Gemini unavailable. model={}, status={}, attempt={}, retryable={}, bodyPreview={}",
                                        model, statusCode, attempt, retryable, preview(rawResponse));
                            } else {
                                logger.warn("Gemini request failed. model={}, status={}, attempt={}, retryable={}, bodyPreview={}",
                                        model, statusCode, attempt, retryable, preview(rawResponse));
                            }
                            return new GeminiAttempt(Optional.empty(), retryable);
                        }

                        try {
                            String text = extractText(rawResponse);
                            if (text == null || text.isBlank()) {
                                logger.warn("Gemini response empty or truncated. model={}, status={}, bodyLength={}",
                                        model, statusCode, length(rawResponse));
                                return new GeminiAttempt(Optional.empty(), false);
                            }
                            logger.info("Gemini response received. model={}, status={}, bodyLength={}", model, statusCode, length(rawResponse));
                            return new GeminiAttempt(Optional.of(text.trim()), false);
                        } catch (Exception exception) {
                            logger.warn("Gemini response parse failed. model={}, status={}, reason={}, bodyPreview={}",
                                    model, statusCode, exception.getMessage(), preview(rawResponse));
                            return new GeminiAttempt(Optional.empty(), false);
                        }
                    });
        } catch (Exception exception) {
            logger.warn("Gemini generation failed. model={}, timeoutSeconds={}, reason={}",
                    model, properties.getTimeoutSeconds(), exception.getMessage());
            return new GeminiAttempt(Optional.empty(), false);
        }
    }

    private Map<String, Object> requestPayload(String prompt) {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "topP", 0.9,
                        "maxOutputTokens", Math.max(256, properties.getMaxOutputTokens())
                )
        );
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())).toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return requestFactory;
    }

    private String extractText(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        if (rawResponse.contains("\"finishReason\":\"MAX_TOKENS\"") || rawResponse.contains("\"finishReason\": \"MAX_TOKENS\"")) {
            logger.warn("AI response truncated by max tokens. model={}, maxOutputTokens={}, responseLength={}, bodyPreview={}",
                    properties.getGemini().getModel(), properties.getMaxOutputTokens(), length(rawResponse), preview(rawResponse));
            return null;
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }
        String finishReason = candidates.get(0).path("finishReason").asText(null);
        if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
            logger.warn("AI response truncated by max tokens. model={}, maxOutputTokens={}, responseLength={}, bodyPreview={}",
                    properties.getGemini().getModel(), properties.getMaxOutputTokens(), length(rawResponse), preview(rawResponse));
            return null;
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        return parts.get(0).path("text").asText(null);
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= RESPONSE_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, RESPONSE_PREVIEW_LIMIT);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private boolean isRateLimitResponse(int statusCode, String rawResponse) {
        if (statusCode == 429) {
            return true;
        }
        if (rawResponse == null) {
            return false;
        }
        String normalized = rawResponse.toLowerCase();
        return normalized.contains("resource_exhausted")
                || normalized.contains("rate limit")
                || normalized.contains("quota");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 503;
    }

    private boolean isServerErrorStatus(int statusCode) {
        return statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void sleepBeforeRetry(String model, int retryNumber) {
        try {
            logger.warn("Retrying Gemini request. model={}, retryNumber={}, delayMillis={}",
                    model, retryNumber, RETRY_DELAY_MILLIS);
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record GeminiAttempt(Optional<String> text, boolean retryable) {
    }
}




