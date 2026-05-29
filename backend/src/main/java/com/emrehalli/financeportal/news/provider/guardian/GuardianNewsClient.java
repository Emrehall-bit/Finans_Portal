package com.emrehalli.financeportal.news.provider.guardian;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GuardianNewsClient {

    private static final Logger logger = LogManager.getLogger(GuardianNewsClient.class);

    private final RestTemplate restTemplate;
    private final GuardianNewsProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<ProviderSyncDiagnostics> lastDiagnostics =
            new AtomicReference<>(ProviderSyncDiagnostics.empty(NewsProviderType.GUARDIAN.name()));

    public GuardianNewsClient(RestTemplate restTemplate, GuardianNewsProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<NewsItemDto> fetchNews() {
        if (!properties.isOperationallyEnabled()) {
            String errorMessage = properties.isEnabled()
                    ? "Provider disabled because Guardian API key is missing"
                    : "Provider disabled by configuration";
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.GUARDIAN.name())
                    .enabled(false)
                    .feedUrlCount(1)
                    .fetched(0)
                    .fetchedFromApi(0)
                    .timeoutCount(0)
                    .parseErrorCount(0)
                    .errorMessage(errorMessage)
                    .lastErrors(List.of(errorMessage))
                    .build());
            return List.of();
        }

        long startedAt = System.nanoTime();
        try {
            String requestUrl = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                    .queryParam("section", "business")
                    .queryParam("tag", "business/economics")
                    .queryParam("order-by", "newest")
                    .queryParam("page-size", 20)
                    .queryParam("show-fields", "thumbnail,trailText,bodyText,shortUrl")
                    .queryParam("api-key", properties.getApiKey())
                    .build(true)
                    .toUriString();

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            String payload = response.getBody();
            List<NewsItemDto> items = parse(payload);
            int fullContentCount = (int) items.stream()
                    .filter(item -> NewsQualityStatus.FULL_CONTENT.name().equals(item.getQualityStatus()))
                    .count();
            int summaryOnlyCount = (int) items.stream()
                    .filter(item -> NewsQualityStatus.SUMMARY_ONLY.name().equals(item.getQualityStatus()))
                    .count();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

            logger.info(
                    "Guardian fetch completed. provider={}, fetchedCount={}, fullContentCount={}, summaryOnlyCount={}, durationMs={}",
                    NewsProviderType.GUARDIAN.name(),
                    items.size(),
                    fullContentCount,
                    summaryOnlyCount,
                    durationMs
            );

            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.GUARDIAN.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(items.size())
                    .fetchedFromApi(items.size())
                    .extractedFullContent(fullContentCount)
                    .apiReturnedCount(items.size())
                    .fullContentEligibleCount(fullContentCount)
                    .timeoutCount(0)
                    .parseErrorCount(0)
                    .firstReturnedTitle(items.isEmpty() ? null : items.get(0).getTitle())
                    .firstReturnedPublishedAt(items.isEmpty() ? null : items.get(0).getPublishedAt())
                    .lastErrors(List.of())
                    .build());
            return items;
        } catch (RestClientException exception) {
            String message = exception.getCause() instanceof java.net.SocketTimeoutException
                    ? "Guardian API fetch failed: Timeout"
                    : "Guardian API fetch failed: " + exception.getClass().getSimpleName();
            logger.error("Guardian API fetch failed", exception);
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.GUARDIAN.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0)
                    .fetchedFromApi(0)
                    .timeoutCount(message.contains("Timeout") ? 1 : 0)
                    .parseErrorCount(0)
                    .errorMessage(message)
                    .lastErrors(List.of(message))
                    .build());
            return List.of();
        } catch (Exception exception) {
            String message = "Guardian API parse failed: " + exception.getClass().getSimpleName();
            logger.error("Guardian API parse failed", exception);
            lastDiagnostics.set(ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.GUARDIAN.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0)
                    .fetchedFromApi(0)
                    .timeoutCount(0)
                    .parseErrorCount(1)
                    .errorMessage(message)
                    .lastErrors(List.of(message))
                    .build());
            return List.of();
        }
    }

    public ProviderSyncDiagnostics getLastDiagnostics() {
        return lastDiagnostics.get();
    }

    List<NewsItemDto> parse(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(payload);
        JsonNode results = root.path("response").path("results");
        if (!results.isArray()) {
            return List.of();
        }

        List<NewsItemDto> items = new ArrayList<>();
        for (JsonNode result : results) {
            NewsItemDto item = mapResult(result);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private NewsItemDto mapResult(JsonNode result) {
        String resultId = textValue(result.get("id"));
        String title = textValue(result.get("webTitle"));
        String webUrl = textValue(result.get("webUrl"));
        String publishedAtRaw = textValue(result.get("webPublicationDate"));

        JsonNode fields = result.path("fields");
        String imageUrl = cleanText(textValue(fields.get("thumbnail")));
        String trailText = cleanText(textValue(fields.get("trailText")));
        String bodyText = cleanText(textValue(fields.get("bodyText")));
        String shortUrl = cleanText(textValue(fields.get("shortUrl")));
        String resolvedUrl = hasText(webUrl) ? webUrl : shortUrl;
        String summary = hasText(bodyText) ? bodyText : trailText;
        String qualityStatus = hasText(bodyText)
                ? NewsQualityStatus.FULL_CONTENT.name()
                : hasText(trailText) ? NewsQualityStatus.SUMMARY_ONLY.name() : NewsQualityStatus.SOURCE_LINK_ONLY.name();

        if (!hasText(title) || !hasText(resolvedUrl) || !hasText(publishedAtRaw)) {
            return null;
        }

        return NewsItemDto.builder()
                .externalId(hasText(resultId) ? "GUARDIAN:" + resultId : "GUARDIAN:" + resolvedUrl)
                .title(title)
                .summary(summary)
                .source("The Guardian")
                .provider(NewsProviderType.GUARDIAN.name())
                .language(properties.getDefaultLanguage())
                .regionScope(properties.getDefaultRegionScope())
                .category(properties.getDefaultCategory())
                .url(resolvedUrl)
                .imageUrl(imageUrl)
                .publishedAt(OffsetDateTime.parse(publishedAtRaw).toLocalDateTime())
                .qualityStatus(qualityStatus)
                .isKapDisclosure(false)
                .build();
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String cleanText(String value) {
        if (!hasText(value)) {
            return null;
        }
        String cleaned = Jsoup.parse(value).text().trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}




