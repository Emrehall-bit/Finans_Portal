package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.common.logging.SensitiveDataMasker;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Slf4j
public abstract class AbstractFxProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    protected AbstractFxProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    protected List<FxRateDto> fetchRates(String url, String headerName, String apiKey, SourceName sourceName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set(headerName, apiKey);
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("{} FX provider returned empty payload. Weekend or holiday assumed.", sourceName);
                return List.of();
            }

            JsonNode root = objectMapper.readTree(body);
            List<FxRateDto> rates = extractRates(root, sourceName);
            if (rates.isEmpty()) {
                log.warn("{} FX provider returned no parsable rates. Weekend or holiday assumed.", sourceName);
            }
            return rates;
        } catch (DataProviderException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            log.warn("FX provider request failed. provider={}, statusCode={}, errorMessage={}",
                    sourceName, exception.getStatusCode().value(), "HTTP " + exception.getStatusCode().value() + " " + exception.getStatusText());
            log.debug("FX provider request failed with stacktrace. provider={}", sourceName, exception);
            throw new DataProviderException("Failed to fetch FX data from " + sourceName, exception);
        } catch (Exception exception) {
            log.warn("FX provider request failed. provider={}, errorMessage={}",
                    sourceName, SensitiveDataMasker.truncate(exception.getMessage(), 500));
            log.debug("FX provider request failed with stacktrace. provider={}", sourceName, exception);
            throw new DataProviderException("Failed to fetch FX data from " + sourceName, exception);
        }
    }

    private List<FxRateDto> extractRates(JsonNode root, SourceName sourceName) {
        List<FxRateDto> results = new ArrayList<>();
        visitNode(root, sourceName, results);
        return results;
    }

    private void visitNode(JsonNode node, SourceName sourceName, List<FxRateDto> results) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                visitNode(item, sourceName, results);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        FxRateDto rate = mapRate(node, sourceName);
        if (rate != null) {
            results.add(rate);
        }

        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            visitNode(elements.next(), sourceName, results);
        }
    }

    private FxRateDto mapRate(JsonNode node, SourceName sourceName) {
        String currencyCode = firstText(node,
                "currencyCode", "currency", "code", "symbol", "parity", "name");
        BigDecimal buyPrice = firstDecimal(node,
                "buyPrice", "buy", "bid", "buying", "alis", "forexBuying", "banknoteBuying");
        BigDecimal sellPrice = firstDecimal(node,
                "sellPrice", "sell", "ask", "selling", "satis", "forexSelling", "banknoteSelling");
        BigDecimal referencePrice = firstDecimal(node,
                "referencePrice", "reference", "mid", "rate", "crossRate", "unitPrice");

        if (currencyCode == null || (buyPrice == null && sellPrice == null && referencePrice == null)) {
            return null;
        }

        return FxRateDto.builder()
                .sourceName(sourceName)
                .currencyCode(normalizeCurrencyCode(currencyCode))
                .buyPrice(buyPrice)
                .sellPrice(sellPrice)
                .referencePrice(referencePrice)
                .dataTimestamp(firstTimestamp(node,
                        "dataTimestamp", "timestamp", "date", "updatedAt", "updateDate", "lastUpdated", "effectiveDate"))
                .build();
    }

    private String normalizeCurrencyCode(String rawValue) {
        return rawValue.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = findFieldIgnoreCase(node, fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = findFieldIgnoreCase(node, fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    try {
                        return new BigDecimal(text.replace(",", ".").trim());
                    } catch (NumberFormatException ignored) {
                        // Ignore non-decimal fields and continue scanning aliases.
                    }
                }
            }
        }
        return null;
    }

    private LocalDateTime firstTimestamp(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = findFieldIgnoreCase(node, fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    try {
                        return LocalDateTime.parse(text);
                    } catch (DateTimeParseException ignored) {
                        try {
                            return OffsetDateTime.parse(text).toLocalDateTime();
                        } catch (DateTimeParseException ignoredAgain) {
                            // Ignore unparsable timestamp fields and continue scanning aliases.
                        }
                    }
                }
            }
        }
        return LocalDateTime.now();
    }

    private JsonNode findFieldIgnoreCase(JsonNode node, String fieldName) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String current = fieldNames.next();
            if (current.equalsIgnoreCase(fieldName)) {
                return node.get(current);
            }
        }
        return null;
    }
}

