package com.emrehalli.financeportal.market.provider.evdsmacro;

import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.provider.evdsmacro.config.EvdsMacroProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvdsMacroClient {

    private static final Logger log = LoggerFactory.getLogger(EvdsMacroClient.class);
    private static final DateTimeFormatter EVDS_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;
    private final EvdsMacroProperties properties;
    private final ObjectMapper objectMapper;

    public EvdsMacroClient(RestTemplate restTemplate,
                           EvdsMacroProperties properties,
                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logApiKeyState() {
        if (properties.getApi() == null || isBlank(properties.getApi().getKey())) {
            log.warn("EVDS macro API key is empty");
        }
    }

    public EvdsResponse fetchSeries(List<SeriesFormulaRequest> requests, LocalDate from, LocalDate to) {
        if (requests == null || requests.isEmpty()) {
            log.info("EVDS macro client fetch skipped: empty request list");
            return new EvdsResponse(List.of());
        }

        List<SeriesFormulaRequest> validRequests = requests.stream()
                .filter(request -> request != null && !isBlank(request.seriesCode()))
                .toList();
        if (validRequests.isEmpty()) {
            log.info("EVDS macro client fetch skipped: no valid request entries");
            return new EvdsResponse(List.of());
        }

        LocalDate endDate = resolveEndDate(to);
        LocalDate startDate = resolveStartDate(from, endDate);

        String url = buildUrl();
        Map<String, Object> requestBody = buildRequestBody(validRequests, startDate, endDate);

        log.info("EVDS macro client request started: requestCount={}, seriesCodes={}, formulas={}, startDate={}, endDate={}",
                validRequests.size(),
                validRequests.stream().map(SeriesFormulaRequest::seriesCode).toList(),
                validRequests.stream().map(SeriesFormulaRequest::formula).toList(),
                EVDS_DATE_FORMATTER.format(startDate),
                EVDS_DATE_FORMATTER.format(endDate));

        HttpHeaders headers = new HttpHeaders();
        headers.set("key", properties.getApi().getKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class
        );

        String rawBody = response.getBody();
        log.info("EVDS macro client response received: status={}, contentType={}",
                response.getStatusCode(), response.getHeaders().getContentType());

        if (isBlank(rawBody)) {
            log.warn("EVDS macro client returned empty response body");
            return new EvdsResponse(List.of());
        }

        if (!looksLikeJson(rawBody)) {
            throw new IllegalStateException("EVDS macro returned non-JSON response body: " + abbreviate(rawBody));
        }

        log.debug("EVDS macro raw response body: {}", abbreviate(rawBody));

        EvdsResponse body = readResponse(rawBody);
        log.info("EVDS macro client request completed: recordCount={}", body.items().size());
        return body;
    }

    public EvdsResponse fetchSeries(String seriesCode, int formula, LocalDate from, LocalDate to) {
        return fetchSeries(List.of(new SeriesFormulaRequest(seriesCode, formula)), from, to);
    }

    private String buildUrl() {
        String baseUrl = properties.getApi().getBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String macroPath = properties.getApi().getMacroPath();
        String normalizedPath = macroPath.startsWith("/") ? macroPath : "/" + macroPath;
        return normalizedBaseUrl + normalizedPath;
    }

    private Map<String, Object> buildRequestBody(List<SeriesFormulaRequest> requests, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("series", joinSeriesCodes(requests));
        body.put("aggregationTypes", joinAggregationTypes(requests.size()));
        body.put("formulas", joinFormulas(requests));
        body.put("startDate", EVDS_DATE_FORMATTER.format(startDate));
        body.put("endDate", EVDS_DATE_FORMATTER.format(endDate));
        body.put("type", "json");
        return body;
    }

    private LocalDate resolveEndDate(LocalDate to) {
        return (to == null ? LocalDate.now() : to).withDayOfMonth(1);
    }

    private LocalDate resolveStartDate(LocalDate from, LocalDate endDate) {
        if (from != null) {
            return from.withDayOfMonth(1);
        }
        return endDate.minusMonths(18).withDayOfMonth(1);
    }

    private String joinSeriesCodes(List<SeriesFormulaRequest> requests) {
        return requests.stream()
                .map(SeriesFormulaRequest::seriesCode)
                .collect(java.util.stream.Collectors.joining("-"));
    }

    private String joinFormulas(List<SeriesFormulaRequest> requests) {
        return requests.stream()
                .map(request -> Integer.toString(request.formula()))
                .collect(java.util.stream.Collectors.joining("-"));
    }

    private String joinAggregationTypes(int requestCount) {
        List<String> aggregationTypes = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            aggregationTypes.add("avg");
        }
        return String.join("-", aggregationTypes);
    }

    private EvdsResponse readResponse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, EvdsResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse EVDS macro JSON response", ex);
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

    public record SeriesFormulaRequest(String seriesCode, int formula) {
    }
}
