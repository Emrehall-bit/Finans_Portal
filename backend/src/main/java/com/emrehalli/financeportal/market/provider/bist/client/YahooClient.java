package com.emrehalli.financeportal.market.provider.bist.client;

import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import com.emrehalli.financeportal.market.provider.bist.dto.YahooQuoteResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class YahooClient {

    private static final Logger log = LoggerFactory.getLogger(YahooClient.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final Duration CREDENTIAL_TTL = Duration.ofHours(6);

    private final RestTemplate restTemplate;
    private final BistProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile String cookieHeader;
    private volatile String crumb;
    private volatile Instant credentialExpiresAt;

    public YahooClient(RestTemplate restTemplate,
                       BistProviderProperties properties,
                       ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.credentialExpiresAt = Instant.EPOCH;
    }

    public FetchResult fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return FetchResult.empty();
        }

        if (!ensureCredentials()) {
            return FetchResult.unauthorized(401);
        }

        return executeQuoteRequest(symbols, false);
    }

    private FetchResult executeQuoteRequest(List<String> symbols, boolean retriedAfterRefresh) {
        URI uri = buildQuoteUri(symbols, crumb);
        log.info("BIST Yahoo request started: symbols={}, retried={}", symbols, retriedAfterRefresh);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(quoteHeaders(cookieHeader)),
                    String.class
            );
            String rawBody = response.getBody();
            if (rawBody == null || rawBody.isBlank()) {
                return FetchResult.empty();
            }

            YahooQuoteResponse body = objectMapper.readValue(rawBody, YahooQuoteResponse.class);
            List<BistQuoteResponse> quotes = body == null || body.quoteResponse() == null || body.quoteResponse().result() == null
                    ? List.of()
                    : body.quoteResponse().result().stream().filter(item -> item != null && item.regularMarketPrice() != null).toList();
            return FetchResult.success(quotes);
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 429) {
                return FetchResult.rateLimited(statusCode);
            }
            if (statusCode == 401 || statusCode == 403) {
                if (!retriedAfterRefresh) {
                    invalidateCredentials();
                    if (ensureCredentials()) {
                        return executeQuoteRequest(symbols, true);
                    }
                }
                return FetchResult.unauthorized(statusCode);
            }

            log.warn("BIST Yahoo request failed: status={}, error={}", statusCode, ex.getMessage());
            return FetchResult.empty();
        } catch (JsonProcessingException ex) {
            log.warn("BIST Yahoo response parse failed: error={}", ex.getMessage(), ex);
            return FetchResult.empty();
        } catch (Exception ex) {
            log.warn("BIST Yahoo request failed: error={}", ex.getMessage(), ex);
            return FetchResult.empty();
        }
    }

    URI buildQuoteUri(List<String> symbols, String crumbValue) {
        return UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getYahoo().getBaseUrl()))
                .path("/v7/finance/quote")
                .queryParam("symbols", String.join(",", symbols))
                .queryParam("crumb", crumbValue)
                .build(true)
                .toUri();
    }

    private synchronized boolean ensureCredentials() {
        Instant now = clock.instant();
        if (hasValidCredentials(now)) {
            return true;
        }

        String refreshedCookieHeader = fetchCookieHeader();
        if (refreshedCookieHeader == null || refreshedCookieHeader.isBlank()) {
            return false;
        }

        String refreshedCrumb = fetchCrumb(refreshedCookieHeader);
        if (refreshedCrumb == null || refreshedCrumb.isBlank()) {
            return false;
        }

        this.cookieHeader = refreshedCookieHeader;
        this.crumb = refreshedCrumb;
        this.credentialExpiresAt = now.plus(CREDENTIAL_TTL);
        log.info("Yahoo credentials refreshed");
        return true;
    }

    private boolean hasValidCredentials(Instant now) {
        return cookieHeader != null
                && !cookieHeader.isBlank()
                && crumb != null
                && !crumb.isBlank()
                && credentialExpiresAt != null
                && credentialExpiresAt.isAfter(now);
    }

    private void invalidateCredentials() {
        cookieHeader = null;
        crumb = null;
        credentialExpiresAt = Instant.EPOCH;
    }

    private String fetchCookieHeader() {
        URI uri = URI.create("https://fc.yahoo.com");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(credentialHeaders()),
                    String.class
            );
            return extractCookieHeader(response.getHeaders());
        } catch (HttpStatusCodeException ex) {
            return extractCookieHeader(ex.getResponseHeaders());
        } catch (Exception ex) {
            log.warn("Yahoo credential cookie fetch failed: error={}", ex.getMessage(), ex);
            return null;
        }
    }

    private String fetchCrumb(String currentCookieHeader) {
        URI uri = URI.create(normalizeBaseUrl(properties.getYahoo().getBaseUrl()) + "/v1/test/getcrumb");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(crumbHeaders(currentCookieHeader)),
                    String.class
            );
            return sanitizeCrumb(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.warn("Yahoo crumb fetch failed: status={}", ex.getStatusCode().value());
            return sanitizeCrumb(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("Yahoo crumb fetch failed: error={}", ex.getMessage(), ex);
            return null;
        }
    }

    private String extractCookieHeader(HttpHeaders headers) {
        if (headers == null || headers.get(HttpHeaders.SET_COOKIE) == null) {
            return null;
        }

        List<String> cookies = new ArrayList<>();
        for (String setCookie : headers.get(HttpHeaders.SET_COOKIE)) {
            if (setCookie == null || setCookie.isBlank()) {
                continue;
            }

            String firstSegment = setCookie.split(";", 2)[0].trim();
            int separatorIndex = firstSegment.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String name = firstSegment.substring(0, separatorIndex).trim().toUpperCase(Locale.ROOT);
            if ("A1".equals(name) || "A3".equals(name) || "B".equals(name)) {
                cookies.add(firstSegment);
            }
        }

        return cookies.isEmpty() ? null : String.join("; ", cookies);
    }

    private String sanitizeCrumb(String rawCrumb) {
        if (rawCrumb == null) {
            return null;
        }

        String sanitized = rawCrumb.trim();
        if (sanitized.isBlank() || sanitized.contains("<")) {
            return null;
        }

        return sanitized;
    }

    private HttpHeaders credentialHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "*/*");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        return headers;
    }

    private HttpHeaders crumbHeaders(String currentCookieHeader) {
        HttpHeaders headers = credentialHeaders();
        headers.set(HttpHeaders.COOKIE, currentCookieHeader);
        return headers;
    }

    private HttpHeaders quoteHeaders(String currentCookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set(HttpHeaders.COOKIE, currentCookieHeader);
        return headers;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Yahoo base URL is not configured");
        }

        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    public record FetchResult(
            List<BistQuoteResponse> responses,
            boolean rateLimited,
            boolean unauthorized,
            int statusCode
    ) {
        public FetchResult {
            responses = responses == null ? List.of() : List.copyOf(responses);
        }

        public static FetchResult success(List<BistQuoteResponse> responses) {
            return new FetchResult(responses, false, false, 200);
        }

        public static FetchResult empty() {
            return new FetchResult(List.of(), false, false, 200);
        }

        public static FetchResult rateLimited(int statusCode) {
            return new FetchResult(List.of(), true, false, statusCode);
        }

        public static FetchResult unauthorized(int statusCode) {
            return new FetchResult(List.of(), false, true, statusCode);
        }
    }
}
