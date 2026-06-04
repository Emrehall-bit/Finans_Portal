package com.emrehalli.financeportal.ai.core.provider;

import com.emrehalli.financeportal.ai.core.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
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
public class GroqAiClient implements LlmClient {

    private static final Logger logger = LogManager.getLogger(GroqAiClient.class);
    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int RESPONSE_PREVIEW_LIMIT = 200;
    private static final int MAX_RETRY_COUNT = 1;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>> inFlightRequests = new ConcurrentHashMap<>();

    public GroqAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public boolean isConfigured() {
        AiProperties.ProviderProperties groq = properties.getGroq();
        return groq.isEnabled()
                && groq.getApiKey() != null && !groq.getApiKey().isBlank()
                && groq.getModel() != null && !groq.getModel().isBlank();
    }

    @Override
    public Optional<String> generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        String model = properties.getGroq().getModel();
        String requestKey = "groq:" + prompt.length() + ":" + prompt.hashCode();
        CompletableFuture<Optional<String>> future = inFlightRequests.computeIfAbsent(requestKey, key ->
                CompletableFuture.supplyAsync(() -> generateWithModel(prompt, model))
        );
        try {
            return future.join();
        } catch (CompletionException exception) {
            logger.warn("Groq single-flight request failed. model={}, reason={}", model, exception.getMessage());
            return Optional.empty();
        } finally {
            inFlightRequests.remove(requestKey, future);
        }
    }

    private Optional<String> generateWithModel(String prompt, String model) {
        Map<String, Object> payload = requestPayload(prompt, model);
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            GroqAttempt result = executeRequest(model, payload, attempt);
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

    private GroqAttempt executeRequest(String model, Map<String, Object> payload, int attempt) {
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory())
                    .build();

            return client.post()
                    .uri(BASE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getGroq().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        int statusCode = response.getStatusCode().value();
                        String rawResponse = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);

                        if (!response.getStatusCode().is2xxSuccessful()) {
                            boolean retryable = isRetryableStatus(statusCode);
                            if (statusCode == 429) {
                                logger.warn("Groq rate limited. model={}, status={}, attempt={}, retryable={}",
                                        model, statusCode, attempt, retryable);
                            } else if (statusCode == 503) {
                                logger.warn("Groq unavailable. model={}, status={}, attempt={}, retryable={}",
                                        model, statusCode, attempt, retryable);
                            } else {
                                logger.warn("Groq request failed. model={}, status={}, attempt={}, retryable={}, bodyPreview={}",
                                        model, statusCode, attempt, retryable, preview(rawResponse));
                            }
                            return new GroqAttempt(Optional.empty(), retryable);
                        }

                        try {
                            String text = extractText(rawResponse, model);
                            if (text == null || text.isBlank()) {
                                logger.warn("Groq response empty or truncated. model={}, status={}, bodyLength={}",
                                        model, statusCode, length(rawResponse));
                                return new GroqAttempt(Optional.empty(), false);
                            }
                            logger.info("Groq response received. model={}, status={}, bodyLength={}",
                                    model, statusCode, length(rawResponse));
                            return new GroqAttempt(Optional.of(text.trim()), false);
                        } catch (Exception exception) {
                            logger.warn("Groq response parse failed. model={}, status={}, reason={}, bodyPreview={}",
                                    model, statusCode, exception.getMessage(), preview(rawResponse));
                            return new GroqAttempt(Optional.empty(), false);
                        }
                    });
        } catch (Exception exception) {
            logger.warn("Groq generation failed. model={}, timeoutSeconds={}, reason={}",
                    model, properties.getTimeoutSeconds(), exception.getMessage());
            return new GroqAttempt(Optional.empty(), false);
        }
    }

    private Map<String, Object> requestPayload(String prompt, String model) {
        return Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "top_p", 0.9,
                "max_tokens", Math.max(256, properties.getMaxOutputTokens())
        );
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())).toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return factory;
    }

    private String extractText(String rawResponse, String model) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode first = choices.get(0);
        String finishReason = first.path("finish_reason").asText(null);
        if ("length".equalsIgnoreCase(finishReason)) {
            logger.warn("Groq response truncated by max tokens. model={}, maxOutputTokens={}",
                    model, properties.getMaxOutputTokens());
            return null;
        }
        return first.path("message").path("content").asText(null);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 503;
    }

    private void sleepBeforeRetry(String model, int retryNumber) {
        try {
            logger.warn("Retrying Groq request. model={}, retryNumber={}, delayMillis={}",
                    model, retryNumber, RETRY_DELAY_MILLIS);
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String preview(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= RESPONSE_PREVIEW_LIMIT ? normalized : normalized.substring(0, RESPONSE_PREVIEW_LIMIT);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private record GroqAttempt(Optional<String> text, boolean retryable) {
    }
}




