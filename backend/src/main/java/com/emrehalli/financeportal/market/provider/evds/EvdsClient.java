package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class EvdsClient {

    private static final Logger log = LoggerFactory.getLogger(EvdsClient.class);
    private static final DateTimeFormatter EVDS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;
    private final EvdsProperties properties;
    private final ObjectMapper objectMapper;

    public EvdsClient(RestTemplate restTemplate,
                      EvdsProperties properties,
                      ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logApiKeyState() {
        if (properties.getApi() == null || isBlank(properties.getApi().getKey())) {
            log.warn("EVDS API key is empty");
        }
    }

    public EvdsResponse fetchSeries(List<String> seriesCodes, LocalDate from, LocalDate to) {
        if (seriesCodes == null || seriesCodes.isEmpty()) {
            log.info("EVDS client fetch skipped: empty series code list");
            return new EvdsResponse(List.of());
        }

        LocalDate endDate = to == null ? LocalDate.now() : to;
        LocalDate startDate = from == null
                ? endDate.minusDays(Math.max(properties.getHistory().getSchedulerLookbackDays(), 1))
                : from;

        String url = buildSeriesUrl(seriesCodes, startDate, endDate);

        log.info("EVDS client request started: seriesCount={}, startDate={}, endDate={}, url={}",
                seriesCodes.size(), startDate, endDate, url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("key", properties.getApi().getKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        String rawBody = response.getBody();

        log.info("EVDS client response received: status={}, contentType={}",
                response.getStatusCode(), response.getHeaders().getContentType());
        log.debug("EVDS client raw response body: {}", rawBody);

        if (isBlank(rawBody)) {
            log.warn("EVDS client returned empty response body");
            return new EvdsResponse(List.of());
        }

        if (!looksLikeJson(rawBody)) {
            throw new IllegalStateException(
                    "EVDS returned non-JSON response body: " + abbreviate(rawBody)
            );
        }

        EvdsResponse body = readResponse(rawBody);
        log.info("EVDS client request completed: recordCount={}", body.items().size());
        return body;
    }

    private String buildSeriesUrl(List<String> seriesCodes, LocalDate startDate, LocalDate endDate) {
        String baseUrl = properties.getApi().getUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        return normalizedBaseUrl
                + "/series=" + String.join("-", seriesCodes)
                + "&startDate=" + EVDS_DATE_FORMATTER.format(startDate)
                + "&endDate=" + EVDS_DATE_FORMATTER.format(endDate)
                + "&type=json";
    }

    private EvdsResponse readResponse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, EvdsResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse EVDS JSON response", ex);
        }
    }

    private boolean looksLikeJson(String rawBody) {
        String trimmed = rawBody.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String abbreviate(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300
                ? normalized
                : normalized.substring(0, 300) + "...";
    }
}
