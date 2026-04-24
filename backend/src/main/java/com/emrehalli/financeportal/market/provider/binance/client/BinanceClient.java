package com.emrehalli.financeportal.market.provider.binance.client;

import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceKlineResponse;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class BinanceClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceClient.class);

    private final RestTemplate restTemplate;
    private final BinanceProviderProperties properties;
    private final ObjectMapper objectMapper;

    public BinanceClient(RestTemplate restTemplate,
                         BinanceProviderProperties properties,
                         ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<BinanceTickerResponse> fetchTickers(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("Binance client fetch skipped: empty symbol list");
            return List.of();
        }

        URI uri = buildTickerUri(symbols);
        log.info("Binance client request started: symbolCount={}, uri={}", symbols.size(), uri);

        ResponseEntity<BinanceTickerResponse[]> response = restTemplate.getForEntity(uri, BinanceTickerResponse[].class);
        BinanceTickerResponse[] body = response.getBody();

        if (body == null || body.length == 0) {
            log.info("Binance client request completed: empty response body");
            return List.of();
        }

        List<BinanceTickerResponse> tickers = Arrays.stream(body)
                .filter(Objects::nonNull)
                .toList();

        log.info("Binance client request completed: quoteCount={}", tickers.size());
        return tickers;
    }

    public List<BinanceKlineResponse> fetchDailyKlines(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank()) {
            log.info("Binance client kline fetch skipped: empty symbol");
            return List.of();
        }

        int limit = resolveKlineLimit(from, to);
        URI uri = buildKlinesUri(symbol.trim(), limit);
        log.info("Binance client kline request started: symbol={}, limit={}, uri={}", symbol, limit, uri);

        ResponseEntity<Object[][]> response = restTemplate.getForEntity(uri, Object[][].class);
        Object[][] body = response.getBody();

        if (body == null || body.length == 0) {
            log.info("Binance client kline request completed: symbol={}, empty response body", symbol);
            return List.of();
        }

        List<BinanceKlineResponse> klines = Arrays.stream(body)
                .filter(Objects::nonNull)
                .map(BinanceKlineResponse::fromArray)
                .filter(Objects::nonNull)
                .toList();

        log.info("Binance client kline request completed: symbol={}, klineCount={}", symbol, klines.size());
        return klines;
    }

    URI buildTickerUri(List<String> symbols) {
        String encodedSymbols = UriUtils.encodeQueryParam(toJson(symbols), StandardCharsets.UTF_8);

        return UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path("/api/v3/ticker/24hr")
                .query("symbols=" + encodedSymbols)
                .build(true)
                .toUri();
    }

    URI buildKlinesUri(String symbol, int limit) {
        return UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path("/api/v3/klines")
                .queryParam("symbol", symbol)
                .queryParam("interval", "1d")
                .queryParam("limit", limit)
                .build(true)
                .toUri();
    }

    private int resolveKlineLimit(LocalDate from, LocalDate to) {
        LocalDate endDate = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate startDate = from == null ? endDate.minusDays(364) : from;
        long requestedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long safeLimit = Math.max(requestedDays, 1L);
        return (int) Math.min(safeLimit, 1000L);
    }

    private String toJson(List<String> symbols) {
        try {
            return objectMapper.writeValueAsString(symbols);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Binance symbols request", ex);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Binance base URL is not configured");
        }

        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}
