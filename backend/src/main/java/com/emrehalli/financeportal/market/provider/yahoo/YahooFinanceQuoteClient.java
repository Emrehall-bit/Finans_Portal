package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.common.logging.SensitiveDataMasker;
import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared HTTP client for Yahoo Finance quote API.
 * Used by index and commodity providers to avoid duplicating HTTP logic.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceQuoteClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Fetches raw quote items from Yahoo Finance for the given Yahoo symbols.
     * Returns an empty list if cookie/crumb is not configured.
     * Throws {@link DataProviderException} on HTTP or parse errors.
     */
    public List<YahooQuoteItem> fetchQuotes(List<String> yahooSymbols) {
        if (yahooSymbols == null || yahooSymbols.isEmpty()) {
            return List.of();
        }

        String cookie = props.getProviders().getYahoo().getCookie();
        String crumb  = props.getProviders().getYahoo().getCrumb();
        if (!hasText(cookie) || !hasText(crumb)) {
            log.warn("Yahoo Finance cookie/crumb yapılandırılmamış, sorgu atlanıyor");
            throw new DataProviderException("Yahoo Finance cookie/crumb is not configured");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(props.getProviders().getYahoo().getBaseUrl() + "/v7/finance/quote")
                .queryParam("symbols", yahooSymbols.stream().collect(Collectors.joining(",")))
                .queryParam("fields",
                        "regularMarketPrice,regularMarketChangePercent,regularMarketPreviousClose," +
                        "regularMarketDayHigh,regularMarketDayLow,regularMarketVolume")
                .queryParam("crumb", crumb)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.COOKIE, cookie);

        try {
            log.debug("Yahoo Finance quote request. maskedUrl={}", SensitiveDataMasker.maskUri(url));
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new DataProviderException("Yahoo Finance returned empty payload");
            }

            JsonNode result = objectMapper.readTree(body)
                    .path("quoteResponse")
                    .path("result");

            if (!result.isArray()) {
                throw new DataProviderException("Yahoo Finance payload has no result array");
            }

            List<YahooQuoteItem> items = new ArrayList<>();
            for (JsonNode node : result) {
                String symbol = node.path("symbol").asText(null);
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                items.add(new YahooQuoteItem(
                        symbol,
                        decimal(node, "regularMarketPrice"),
                        decimal(node, "regularMarketChangePercent"),
                        decimal(node, "regularMarketPreviousClose"),
                        decimal(node, "regularMarketDayHigh"),
                        decimal(node, "regularMarketDayLow"),
                        longValue(node, "regularMarketVolume")
                ));
            }
            return items;

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Yahoo Finance cookie süresi dolmuş, manuel güncelleme gerekiyor");
            throw new DataProviderException("Yahoo Finance cookie expired", e);
        } catch (HttpStatusCodeException e) {
            throw new DataProviderException("Yahoo Finance HTTP error: " + e.getStatusCode().value(), e);
        } catch (DataProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DataProviderException("Failed to parse Yahoo Finance payload", e);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return n.isNumber() ? n.decimalValue() : new BigDecimal(n.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (!n.isMissingNode() && !n.isNull() && n.isNumber()) ? n.longValue() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Raw quote item returned by Yahoo Finance API.
     */
    public record YahooQuoteItem(
            String yahooSymbol,
            BigDecimal price,
            BigDecimal changePercent,
            BigDecimal previousClose,
            BigDecimal dayHigh,
            BigDecimal dayLow,
            Long volume
    ) {}
}
