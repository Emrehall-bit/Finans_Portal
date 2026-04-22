package com.emrehalli.financeportal.market.provider.yahoo.client;

import com.emrehalli.financeportal.config.YahooFinanceProperties;
import com.emrehalli.financeportal.market.provider.yahoo.dto.YahooFinanceQuoteResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class YahooFinanceClient {

    private static final Logger logger = LogManager.getLogger(YahooFinanceClient.class);

    private final YahooFinanceProperties yahooFinanceProperties;
    private final RestTemplate restTemplate;

    public YahooFinanceClient(YahooFinanceProperties yahooFinanceProperties,
                              RestTemplate restTemplate) {
        this.yahooFinanceProperties = yahooFinanceProperties;
        this.restTemplate = restTemplate;
    }

    public YahooFinanceQuoteResponse fetchSnapshotData() {
        if (!yahooFinanceProperties.isEnabled()) {
            logger.debug("Yahoo Finance snapshot fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (yahooFinanceProperties.getBaseUrl() == null || yahooFinanceProperties.getBaseUrl().isBlank()) {
            logger.warn("Yahoo Finance provider is enabled but base URL is missing. Snapshot fetch skipped.");
            return emptyResponse();
        }

        List<String> symbols = yahooFinanceProperties.getSymbols().stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::trim)
                .toList();

        if (symbols.isEmpty()) {
            logger.warn("Yahoo Finance provider is enabled but no symbols are configured. Snapshot fetch skipped.");
            return emptyResponse();
        }

        String endpoint = UriComponentsBuilder.fromHttpUrl(yahooFinanceProperties.getBaseUrl())
                .path("/v7/finance/quote")
                .queryParam("symbols", String.join(",", symbols))
                .toUriString();

        logger.info("Yahoo Finance snapshot fetch started for {} symbols against {}", symbols.size(), endpoint);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
            List<Map<String, Object>> items = extractQuoteItems(response.getBody());
            logger.info("Yahoo Finance snapshot fetch completed with {} raw items", items.size());
            return YahooFinanceQuoteResponse.builder()
                    .fetchedAt(LocalDateTime.now())
                    .items(items)
                    .build();
        } catch (RestClientException e) {
            logger.warn("Yahoo Finance snapshot fetch failed for endpoint {}", endpoint, e);
            return emptyResponse();
        }
    }

    public YahooFinanceQuoteResponse fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        if (!yahooFinanceProperties.isEnabled()) {
            logger.debug("Yahoo Finance historical fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (yahooFinanceProperties.getBaseUrl() == null || yahooFinanceProperties.getBaseUrl().isBlank()) {
            logger.warn("Yahoo Finance provider is enabled but base URL is missing. Historical fetch skipped for {}.", symbol);
            return emptyResponse();
        }

        logger.info("Yahoo Finance historical fetch requested for {} between {} and {}", symbol, startDate, endDate);
        logger.info("Yahoo Finance historical integration is not implemented yet. Returning empty response for {}", symbol);
        return emptyResponse();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractQuoteItems(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }

        Object quoteResponse = body.get("quoteResponse");
        if (!(quoteResponse instanceof Map<?, ?> quoteMap)) {
            return List.of();
        }

        Object result = quoteMap.get("result");
        if (!(result instanceof List<?> resultList)) {
            return List.of();
        }

        return resultList.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private YahooFinanceQuoteResponse emptyResponse() {
        return YahooFinanceQuoteResponse.builder()
                .fetchedAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }
}
