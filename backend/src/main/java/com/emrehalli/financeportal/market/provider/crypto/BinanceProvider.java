package com.emrehalli.financeportal.market.provider.crypto;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.crypto.dto.CryptoTickerDto;
import com.emrehalli.financeportal.market.support.BinancePairMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Binance crypto market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BinanceProvider implements MarketDataProvider {

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BinancePairMapper binancePairMapper;

    @Override
    public String getSourceName() {
        return SourceName.BINANCE.name();
    }

    @Override
    public List<CryptoTickerDto> fetch() {
        List<CryptoTickerDto> tickers = fetchFromApi();
        if (tickers.isEmpty()) {
            log.warn("Binance crypto provider returned no ticker data for configured pairs {}", binancePairMapper.getAllSymbols());
        }
        return tickers;
    }

    private List<CryptoTickerDto> fetchFromApi() {
        List<String> symbols = binancePairMapper.getAllSymbols();
        if (symbols.isEmpty()) {
            log.warn("Binance pair mapper contains no TRY pairs. Skipping ticker fetch.");
            return Collections.emptyList();
        }

        String baseUrl = props.getProviders().getBinance().getBaseUrl();
        String url;
        try {
            url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/ticker/24hr")
                    .queryParam("symbols", objectMapper.writeValueAsString(symbols))
                    .build()
                    .toUriString();
        } catch (Exception exception) {
            log.error("Failed to build Binance ticker request URL", exception);
            throw new DataProviderException("Failed to build Binance ticker request URL", exception);
        }

        try {
            String responseBody = restTemplate.getForObject(url, String.class);
            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Binance provider returned empty ticker payload for symbols {}", symbols);
                return Collections.emptyList();
            }

            List<Map<String, String>> payload = objectMapper.readValue(
                    responseBody,
                    new TypeReference<>() {
                    }
            );
            if (payload.isEmpty()) {
                log.warn("Binance provider returned empty ticker list for symbols {}", symbols);
                return Collections.emptyList();
            }

            LocalDateTime timestamp = LocalDateTime.now();
            List<CryptoTickerDto> tickers = new ArrayList<>();
            for (Map<String, String> item : payload) {
                if (item == null) {
                    continue;
                }

                String symbol = item.get("symbol");
                String priceText = item.get("lastPrice");
                String changePercentText = item.get("priceChangePercent");
                if (priceText == null || priceText.isBlank() || !binancePairMapper.isSupported(symbol)) {
                    continue;
                }

                try {
                    BigDecimal dailyChangePercent = null;
                    if (changePercentText != null && !changePercentText.isBlank()) {
                        dailyChangePercent = new BigDecimal(changePercentText);
                    }

                    tickers.add(CryptoTickerDto.builder()
                            .symbol(binancePairMapper.toDisplayCode(symbol))
                            .price(new BigDecimal(priceText))
                            .dailyChangePercent(dailyChangePercent)
                            .dataTimestamp(timestamp)
                            .build());
                } catch (NumberFormatException exception) {
                    log.warn("Failed to parse Binance ticker price for symbol={}", symbol, exception);
                }
            }

            if (tickers.isEmpty()) {
                log.warn("Binance provider returned no valid ticker records for symbols {}", symbols);
            }
            return tickers;
        } catch (HttpStatusCodeException exception) {
            log.error("Failed to fetch crypto data from Binance. HTTP status={}", exception.getStatusCode().value(), exception);
            throw new DataProviderException("Failed to fetch crypto data from Binance", exception);
        } catch (DataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to parse Binance ticker response", exception);
            throw new DataProviderException("Failed to parse Binance ticker response", exception);
        }
    }
}




