package com.emrehalli.financeportal.market.provider.binance.client;

import com.emrehalli.financeportal.config.BinanceProperties;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerItem;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class BinanceClient {

    private static final Logger logger = LogManager.getLogger(BinanceClient.class);

    private final BinanceProperties binanceProperties;
    private final RestTemplate restTemplate;

    public BinanceClient(BinanceProperties binanceProperties,
                         RestTemplate restTemplate) {
        this.binanceProperties = binanceProperties;
        this.restTemplate = restTemplate;
    }

    public BinanceTickerResponse fetchSnapshotData() {
        if (!binanceProperties.isEnabled()) {
            logger.debug("Binance snapshot fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (binanceProperties.getBaseUrl() == null || binanceProperties.getBaseUrl().isBlank()) {
            logger.warn("Binance provider is enabled but base URL is missing. Snapshot fetch skipped.");
            return emptyResponse();
        }

        List<String> symbols = binanceProperties.getSymbols().stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::trim)
                .toList();

        if (symbols.isEmpty()) {
            logger.warn("Binance provider is enabled but no symbols are configured. Snapshot fetch skipped.");
            return emptyResponse();
        }

        // DEĞİŞİKLİK BURADA: String endpoint yerine, parametreleri encode edilmiş bir URI nesnesi oluşturuyoruz.
        URI targetUri = UriComponentsBuilder.fromHttpUrl(binanceProperties.getBaseUrl())
                .path("/api/v3/ticker/24hr")
                .queryParam("symbols", toJsonArray(symbols))
                .build()
                .encode() // URL encode işlemini burada güvenli şekilde ve sadece 1 kez yapıyoruz
                .toUri();

        logger.info("Binance snapshot fetch started for {} symbols against {}", symbols.size(), targetUri);

        try {
            // DEĞİŞİKLİK BURADA: restTemplate'e String yerine oluşturduğumuz targetUri nesnesini veriyoruz.
            ResponseEntity<BinanceTickerItem[]> response = restTemplate.getForEntity(targetUri, BinanceTickerItem[].class);
            List<BinanceTickerItem> items = response.getBody() == null
                    ? List.of()
                    : Arrays.stream(response.getBody()).toList();

            logger.info("Binance snapshot fetch completed with {} raw items", items.size());
            return BinanceTickerResponse.builder()
                    .fetchedAt(LocalDateTime.now())
                    .items(items)
                    .build();
        } catch (RestClientException e) {
            logger.warn("Binance snapshot fetch failed for endpoint {}", targetUri, e);
            return emptyResponse();
        }
    }

    public BinanceTickerResponse fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        if (!binanceProperties.isEnabled()) {
            logger.debug("Binance historical fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (binanceProperties.getBaseUrl() == null || binanceProperties.getBaseUrl().isBlank()) {
            logger.warn("Binance provider is enabled but base URL is missing. Historical fetch skipped for {}.", symbol);
            return emptyResponse();
        }

        logger.info("Binance historical fetch requested for {} between {} and {}", symbol, startDate, endDate);
        logger.info("Binance historical integration is not implemented yet. Returning empty response for {}", symbol);
        return emptyResponse();
    }

    private String toJsonArray(List<String> symbols) {
        return "[" + symbols.stream()
                .map(symbol -> "\"" + symbol + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private BinanceTickerResponse emptyResponse() {
        return BinanceTickerResponse.builder()
                .fetchedAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }
}
