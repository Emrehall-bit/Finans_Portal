package com.emrehalli.financeportal.market.provider.stock;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Yahoo Finance stock market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceStockProvider implements MarketDataProvider {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final BistSymbolRegistry bistSymbolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public String getSourceName() {
        return SourceName.YAHOO_FINANCE.name();
    }

    @Override
    public List<StockPriceDto> fetch() {
        log.error(">>> YAHOO FETCH CALLED, cookie={}, crumb={}",
                props.getProviders().getYahoo().getCookie() != null ? "SET" : "NULL",
                props.getProviders().getYahoo().getCrumb() != null ? "SET" : "NULL");
        try {
            List<StockPriceDto> quotes = fetchFromApi();
            if (quotes.isEmpty()) {
                log.warn("Yahoo Finance stock provider returned no stock quote data for symbols {}", bistSymbolRegistry.getAllSymbols());
            }
            return quotes;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch stock quote data from Yahoo Finance", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch stock quote data from Yahoo Finance", exception);
            throw new DataProviderException("Failed to fetch stock quote data from Yahoo Finance", exception);
        }
    }

    private List<StockPriceDto> fetchFromApi() {
        String cookie = props.getProviders().getYahoo().getCookie();
        String crumb = props.getProviders().getYahoo().getCrumb();
        if (!hasText(cookie) || !hasText(crumb)) {
            log.warn("Yahoo Finance cookie/crumb yapılandırılmamış, hisse verisi atlanıyor");
            throw new DataProviderException("Yahoo Finance cookie/crumb is not configured");
        }

        List<String> yahooSymbols = bistSymbolRegistry.getAllYahooSymbols();
        if (yahooSymbols.isEmpty()) {
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(props.getProviders().getYahoo().getBaseUrl() + "/v7/finance/quote")
                .queryParam("symbols", yahooSymbols.stream().collect(Collectors.joining(",")))
                .queryParam(
                        "fields",
                        "regularMarketPrice,regularMarketChangePercent,regularMarketPreviousClose,regularMarketDayHigh,regularMarketDayLow,regularMarketVolume"
                )
                .queryParam("crumb", crumb)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.COOKIE, cookie);

        try {
            log.error(">>> YAHOO URL: {}", url);
            ResponseEntity<String> response;
            try {
                log.error(">>> BEFORE EXCHANGE");
                response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                log.error(">>> AFTER EXCHANGE: {}", response.getStatusCode());
            } catch (Throwable t) {
                log.error(">>> EXCHANGE EXCEPTION: {}", t.getClass().getName() + ": " + t.getMessage());
                throw new DataProviderException("exchange failed", t);
            }
            log.error(">>> YAHOO RESPONSE STATUS: {}", response.getStatusCode());
            log.error(">>> YAHOO RESPONSE BODY: {}", response.getBody() != null ? response.getBody() : "null");

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new DataProviderException("Yahoo Finance returned empty stock quote payload");
            }

            JsonNode result = objectMapper.readTree(responseBody)
                    .path("quoteResponse")
                    .path("result");

            if (!result.isArray()) {
                throw new DataProviderException("Yahoo Finance stock quote payload has no result array");
            }

            Instant timestamp = Instant.now();
            List<StockPriceDto> prices = new ArrayList<>();
            for (JsonNode item : result) {
                String yahooSymbol = item.path("symbol").asText(null);
                BigDecimal price = decimalValue(item, "regularMarketPrice");
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Skipping Yahoo Finance stock quote with invalid price. symbol={}, price={}", yahooSymbol, price);
                    continue;
                }

                String symbol = normalizeBistSymbol(yahooSymbol);
                if (symbol == null || symbol.isBlank()) {
                    log.warn("Skipping Yahoo Finance stock quote with invalid symbol {}", yahooSymbol);
                    continue;
                }

                prices.add(new StockPriceDto(
                        symbol,
                        yahooSymbol,
                        price,
                        decimalValue(item, "regularMarketChangePercent"),
                        decimalValue(item, "regularMarketPreviousClose"),
                        decimalValue(item, "regularMarketDayHigh"),
                        decimalValue(item, "regularMarketDayLow"),
                        longValue(item, "regularMarketVolume"),
                        SourceName.YAHOO_FINANCE.name(),
                        timestamp
                ));
            }

            return prices;
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Yahoo Finance cookie süresi dolmuş, manuel güncelleme gerekiyor");
            throw new DataProviderException("Yahoo Finance cookie expired, manual refresh required", exception);
        } catch (HttpStatusCodeException exception) {
            throw new DataProviderException("Failed to fetch stock quote data from Yahoo Finance: HTTP " + exception.getStatusCode().value(), exception);
        } catch (DataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DataProviderException("Failed to parse stock quote data from Yahoo Finance", exception);
        }
    }

    private String normalizeBistSymbol(String yahooSymbol) {
        if (yahooSymbol == null || yahooSymbol.isBlank()) {
            return null;
        }
        return yahooSymbol.endsWith(".IS")
                ? yahooSymbol.substring(0, yahooSymbol.length() - 3)
                : yahooSymbol;
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

    private Long longValue(JsonNode item, String fieldName) {
        JsonNode node = item.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isNumber() ? node.longValue() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
