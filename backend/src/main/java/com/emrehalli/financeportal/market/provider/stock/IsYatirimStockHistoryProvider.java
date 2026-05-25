package com.emrehalli.financeportal.market.provider.stock;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Is Yatirim stock history provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IsYatirimStockHistoryProvider {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Value("${market.providers.isyatirim.base-url}")
    private String baseUrl;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public List<StockHistoryDto> fetchHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching Is Yatirim stock history. symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/_layouts/15/Isyatirim.Website/Common/Data.aspx/HisseTekil")
                .queryParam("hisse", symbol)
                .queryParam("startdate", startDate.format(DATE_FORMATTER))
                .queryParam("enddate", endDate.format(DATE_FORMATTER))
                .build()
                .toUriString();

        try {
            String responseBody = restClient.get()
                    .uri(url)
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new DataProviderException("Is Yatirim returned empty stock history payload");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.path("ok").asBoolean(false)) {
                throw new DataProviderException("Is Yatirim stock history response returned ok=false");
            }

            JsonNode value = root.path("value");
            if (!value.isArray() || value.isEmpty()) {
                log.info("Completed Is Yatirim stock history fetch. symbol={}, records=0", symbol);
                return List.of();
            }

            List<StockHistoryDto> history = new ArrayList<>();
            for (JsonNode item : value) {
                LocalDate priceDate = LocalDate.parse(item.path("HGDG_TARIH").asText(), DATE_FORMATTER);
                history.add(new StockHistoryDto(
                        priceDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                        decimalValue(item, "HGDG_AOF"),
                        decimalValue(item, "HGDG_MAX"),
                        decimalValue(item, "HGDG_MIN"),
                        decimalValue(item, "HGDG_KAPANIS"),
                        decimalValue(item, "HGDG_HACIM")
                ));
            }

            log.info("Completed Is Yatirim stock history fetch. symbol={}, records={}", symbol, history.size());
            return history;
        } catch (RestClientResponseException exception) {
            log.error("Failed to fetch Is Yatirim stock history. symbol={}, status={}", symbol, exception.getStatusCode().value(), exception);
            throw new DataProviderException("Failed to fetch stock history from Is Yatirim", exception);
        } catch (DataProviderException exception) {
            log.error("Failed to fetch Is Yatirim stock history. symbol={}", symbol, exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to parse Is Yatirim stock history. symbol={}", symbol, exception);
            throw new DataProviderException("Failed to parse stock history from Is Yatirim", exception);
        }
    }

    public List<StockHistoryDto> fetch(String symbol, LocalDate startDate, LocalDate endDate) {
        return fetchHistory(symbol, startDate, endDate);
    }

    private BigDecimal decimalValue(JsonNode item, String fieldName) {
        JsonNode node = item.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}



