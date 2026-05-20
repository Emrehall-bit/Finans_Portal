package com.emrehalli.financeportal.news.provider.world;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.provider.common.ProviderSyncDiagnostics;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import com.emrehalli.financeportal.news.provider.rss.RssProviderRelevanceFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WorldNewsApiClient {

    private static final Logger logger = LogManager.getLogger(WorldNewsApiClient.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WorldNewsApiProperties properties;
    private final RssFeedSupport rssFeedSupport;
    private final RssProviderRelevanceFilter relevanceFilter;
    private final AtomicReference<ProviderSyncDiagnostics> lastDiagnostics =
            new AtomicReference<>(ProviderSyncDiagnostics.empty(NewsProviderType.WORLD_NEWS_API.name()));

    public WorldNewsApiClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            WorldNewsApiProperties properties,
            RssFeedSupport rssFeedSupport,
            RssProviderRelevanceFilter relevanceFilter
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.rssFeedSupport = rssFeedSupport;
        this.relevanceFilter = relevanceFilter;
    }

    public List<NewsItemDto> fetchLatestNews() {
        return fetchLatestNews(properties.getMaxItemsPerSync());
    }

    public ProviderSyncDiagnostics getLastDiagnostics() {
        return lastDiagnostics.get();
    }

    public List<NewsItemDto> fetchLatestNews(int limit) {
        return fetchLatestNewsWithDiagnostics(limit).items();
    }

    FetchResult fetchLatestNewsWithDiagnostics(int limit) {
        if (!isProviderEnabled()) {
            String errorMessage = !properties.isEnabled()
                    ? "Provider disabled by configuration"
                    : "WORLD_NEWS_API_KEY is missing";
            ProviderSyncDiagnostics diagnostics = ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.WORLD_NEWS_API.name())
                    .enabled(false)
                    .feedUrlCount(1)
                    .fetched(0)
                    .fetchedFromFeed(0)
                    .fetchedFromApi(0)
                    .canonicalResolved(0)
                    .canonicalFailed(0)
                    .extractedFullContent(0)
                    .skippedFullContentNotAvailable(0)
                    .skippedCanonicalNotResolved(0)
                    .skippedByRelevance(0)
                    .apiQuotaLeft(null)
                    .apiQuotaUsed(null)
                    .apiQuotaRequest(null)
                    .errorMessage(errorMessage)
                    .lastErrors(List.of(errorMessage))
                    .build();
            lastDiagnostics.set(diagnostics);
            return new FetchResult(List.of(), 0, 0, 0, errorMessage, List.of(errorMessage), null, null, null);
        }

        try {
            ResponseEntity<String> response = newWorldNewsRestTemplate().exchange(
                    buildSearchUri(limit),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );
            return parseResponse(response);
        } catch (RestClientException exception) {
            String errorMessage = resolveFetchErrorMessage(exception);
            logger.warn("World News API fetch failed. reason: {}", exception.getMessage());
            ProviderSyncDiagnostics diagnostics = ProviderSyncDiagnostics.builder()
                    .provider(NewsProviderType.WORLD_NEWS_API.name())
                    .enabled(true)
                    .feedUrlCount(1)
                    .fetched(0)
                    .fetchedFromFeed(0)
                    .fetchedFromApi(0)
                    .canonicalResolved(0)
                    .canonicalFailed(0)
                    .extractedFullContent(0)
                    .skippedFullContentNotAvailable(0)
                    .skippedCanonicalNotResolved(0)
                    .skippedByRelevance(0)
                    .apiQuotaLeft(null)
                    .apiQuotaUsed(null)
                    .apiQuotaRequest(null)
                    .errorMessage(errorMessage)
                    .lastErrors(List.of(errorMessage))
                    .build();
            lastDiagnostics.set(diagnostics);
            return new FetchResult(List.of(), 0, 0, 0, errorMessage, List.of(errorMessage), null, null, null);
        }
    }

    private FetchResult parseResponse(ResponseEntity<String> response) {
        String body = response.getBody();
        Integer quotaLeft = parseIntegerHeader(response.getHeaders(), "X-API-Quota-Left");
        Integer quotaUsed = parseIntegerHeader(response.getHeaders(), "X-API-Quota-Used");
        Integer quotaRequest = parseIntegerHeader(response.getHeaders(), "X-API-Quota-Request");
        if (body == null || body.isBlank()) {
            String errorMessage = "World News API response was empty";
            ProviderSyncDiagnostics diagnostics = buildDiagnostics(0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest, List.of());
            lastDiagnostics.set(diagnostics);
            return new FetchResult(List.of(), 0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest);
        }

        try {
            JsonNode root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
            JsonNode newsNodes = root.path("news");
            if (!newsNodes.isArray()) {
                String errorMessage = "World News API returned no news array";
                ProviderSyncDiagnostics diagnostics = buildDiagnostics(0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest, List.of());
                lastDiagnostics.set(diagnostics);
                return new FetchResult(List.of(), 0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest);
            }

            List<NewsItemDto> items = new ArrayList<>();
            List<String> lastErrors = new ArrayList<>();
            Set<String> seenKeys = new LinkedHashSet<>();
            int fetchedFromApi = 0;
            int skippedFullContent = 0;
            int skippedByRelevance = 0;

            for (JsonNode node : newsNodes) {
                fetchedFromApi++;
                String title = text(node, "title");
                String fullContent = text(node, "text", "body", "content", "fullText", "full_text");
                String summary = text(node, "summary", "description");
                String sourceUrl = text(node, "url");
                String sourceName = resolveSourceName(node);
                String imageUrl = text(node, "image", "image_url");
                String language = rssFeedSupport.firstNonBlank(text(node, "language"), properties.getLanguage());
                String category = normalizeCategory(title, summary, sourceUrl, node);

                if (!relevanceFilter.isRelevant(NewsProviderType.WORLD_NEWS_API, title, summary, category, sourceUrl)) {
                    skippedByRelevance++;
                    continue;
                }

                if (!hasText(fullContent) || fullContent.trim().length() < properties.getMinContentLength()) {
                    skippedFullContent++;
                    continue;
                }

                String dedupeKey = dedupeKey(sourceUrl, title);
                if (seenKeys.contains(dedupeKey)) {
                    continue;
                }
                seenKeys.add(dedupeKey);

                LocalDateTime publishedAt = rssFeedSupport.parsePublishedAt(
                        text(node, "publish_date", "publishDate", "published_at"),
                        logger,
                        "WORLD_NEWS_API"
                );

                items.add(NewsItemDto.builder()
                        .externalId(rssFeedSupport.resolveExternalId(NewsProviderType.WORLD_NEWS_API.name(), null, sourceUrl))
                        .title(rssFeedSupport.firstNonBlank(title, summary))
                        .summary(rssFeedSupport.clean(fullContent))
                        .source(rssFeedSupport.firstNonBlank(sourceName, "World News API"))
                        .provider(NewsProviderType.WORLD_NEWS_API.name())
                        .language(language)
                        .regionScope("GLOBAL")
                        .category(category)
                        .url(sourceUrl)
                        .imageUrl(rssFeedSupport.clean(imageUrl))
                        .publishedAt(publishedAt)
                        .contentEnrichedAt(LocalDateTime.now())
                        .qualityStatus(NewsQualityStatus.FULL_CONTENT.name())
                        .isKapDisclosure(false)
                        .build());
            }

            ProviderSyncDiagnostics diagnostics = buildDiagnostics(
                    items.size(),
                    fetchedFromApi,
                    skippedFullContent,
                    null,
                    lastErrors,
                    quotaLeft,
                    quotaUsed,
                    quotaRequest,
                    List.of(skippedByRelevance)
            );
            lastDiagnostics.set(diagnostics);
            logger.info(
                    "World News API parsed. fetchedFromApi: {}, savedCandidates: {}, skippedFullContentNotAvailable: {}, skippedByRelevance: {}, quotaLeft: {}",
                    fetchedFromApi,
                    items.size(),
                    skippedFullContent,
                    skippedByRelevance,
                    quotaLeft
            );
            return new FetchResult(items, fetchedFromApi, skippedFullContent, skippedByRelevance, null, lastErrors, quotaLeft, quotaUsed, quotaRequest);
        } catch (Exception exception) {
            String errorMessage = "World News API parse failed";
            logger.warn("World News API parse failed. reason: {}", exception.getMessage());
            ProviderSyncDiagnostics diagnostics = buildDiagnostics(0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest, List.of());
            lastDiagnostics.set(diagnostics);
            return new FetchResult(List.of(), 0, 0, 0, errorMessage, List.of(errorMessage), quotaLeft, quotaUsed, quotaRequest);
        }
    }

    private ProviderSyncDiagnostics buildDiagnostics(
            int fetched,
            int fetchedFromApi,
            int skippedFullContent,
            String errorMessage,
            List<String> lastErrors,
            Integer quotaLeft,
            Integer quotaUsed,
            Integer quotaRequest,
            List<Integer> skippedByRelevanceHolder
    ) {
        int skippedByRelevance = skippedByRelevanceHolder.isEmpty() ? 0 : skippedByRelevanceHolder.get(0);
        return ProviderSyncDiagnostics.builder()
                .provider(NewsProviderType.WORLD_NEWS_API.name())
                .enabled(true)
                .feedUrlCount(1)
                .fetched(fetched)
                .fetchedFromFeed(0)
                .fetchedFromApi(fetchedFromApi)
                .canonicalResolved(0)
                .canonicalFailed(0)
                .extractedFullContent(fetched)
                .skippedFullContentNotAvailable(skippedFullContent)
                .skippedCanonicalNotResolved(0)
                .skippedByRelevance(skippedByRelevance)
                .apiQuotaLeft(quotaLeft)
                .apiQuotaUsed(quotaUsed)
                .apiQuotaRequest(quotaRequest)
                .errorMessage(errorMessage)
                .lastErrors(lastErrors == null ? List.of() : lastErrors)
                .build();
    }

    private URI buildSearchUri(int limit) {
        return UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path("/search-news")
                .queryParam("language", properties.getLanguage())
                .queryParam("categories", properties.getCategories())
                .queryParam("number", Math.max(1, Math.min(limit, properties.getMaxItemsPerSync())))
                .queryParam("sort", "publish-time")
                .build(true)
                .toUri();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", properties.getApiKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String resolveSourceName(JsonNode node) {
        String direct = text(node, "source", "source_name", "sourceName");
        if (hasText(direct)) {
            return direct;
        }
        String url = text(node, "url");
        if (!hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeCategory(String title, String summary, String link, JsonNode node) {
        String apiCategory = text(node, "category");
        String combined = String.join(" ", safe(title), safe(summary), safe(apiCategory), safe(link)).toLowerCase(Locale.ROOT);
        if (containsAny(combined, "fed", "central bank", "ecb", "bank of england", "interest rate", "rates", "policy rate", "inflation")) {
            return "CENTRAL_BANK";
        }
        if (containsAny(combined, "crypto", "bitcoin", "ethereum", "token")) {
            return "CRYPTO";
        }
        if (containsAny(combined, "treasury", "bond", "yield", "debt", "note")) {
            return "BOND";
        }
        if (containsAny(combined, "dollar", "euro", "yen", "currency", "forex", "fx")) {
            return "FX";
        }
        if (containsAny(combined, "oil", "gold", "silver", "gas", "commodity", "opec", "brent", "energy")) {
            return "COMMODITY";
        }
        if (containsAny(combined, "bank", "lender", "credit")) {
            return "BANKING";
        }
        if (containsAny(combined, "stock", "stocks", "share", "shares", "equity", "nasdaq", "s&p", "dow", "market")) {
            return "STOCK";
        }
        if (containsAny(combined, "sanctions", "war", "geopolitical", "security", "tariff")) {
            return "GEOPOLITICS";
        }
        if (containsAny(combined, "earnings", "revenue", "guidance", "merger", "acquisition", "ipo", "company", "companies", "nvidia", "semiconductor", "ai")) {
            return "COMPANY";
        }
        if (containsAny(combined, "economy", "economic", "gdp", "growth", "recession", "jobs", "trade", "supply chain")) {
            return "GENERAL_ECONOMY";
        }
        return hasText(apiCategory) ? apiCategory.toUpperCase(Locale.ROOT).replace('-', '_') : "OTHER";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String dedupeKey(String url, String title) {
        if (hasText(url)) {
            return "URL:" + url.trim().toLowerCase(Locale.ROOT);
        }
        return "TITLE:" + safe(title).trim().toLowerCase(Locale.ROOT);
    }

    private Integer parseIntegerHeader(HttpHeaders headers, String name) {
        try {
            String value = headers.getFirst(name);
            return value == null ? null : Integer.valueOf(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode current = node.path(field);
            if (!current.isMissingNode() && !current.isNull()) {
                String value = current.isTextual() ? current.asText() : current.toString();
                if (hasText(value)) {
                    return rssFeedSupport.clean(value);
                }
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isProviderEnabled() {
        return properties.isEnabled() && hasText(properties.getApiKey());
    }

    private RestTemplate newWorldNewsRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(requestFactory);
    }

    private String resolveFetchErrorMessage(RestClientException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof SocketTimeoutException) {
            return "World News API read timed out";
        }
        return "World News API fetch failed: " + root.getClass().getSimpleName();
    }

    record FetchResult(
            List<NewsItemDto> items,
            int fetchedFromApi,
            int skippedFullContentNotAvailable,
            int skippedByRelevance,
            String errorMessage,
            List<String> lastErrors,
            Integer apiQuotaLeft,
            Integer apiQuotaUsed,
            Integer apiQuotaRequest
    ) {
    }
}
