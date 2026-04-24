package com.emrehalli.financeportal.market.provider.binance.client;

import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceKlineResponse;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinanceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void buildsUriWithSingleEncodedSymbolsParameter() {
        BinanceClient client = new BinanceClient(restTemplate, properties(), new ObjectMapper());
        when(restTemplate.getForEntity(any(URI.class), eq(BinanceTickerResponse[].class)))
                .thenReturn(ResponseEntity.ok(new BinanceTickerResponse[0]));

        client.fetchTickers(List.of("BTCUSDT", "ETHUSDT"));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(BinanceTickerResponse[].class));

        assertThat(uriCaptor.getValue().toString())
                .isEqualTo("https://api.binance.com/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22%5D")
                .doesNotContain("%255B")
                .doesNotContain("%2522");
    }

    @Test
    void mapsArrayResponseToList() {
        BinanceClient client = new BinanceClient(restTemplate, properties(), new ObjectMapper());
        when(restTemplate.getForEntity(any(URI.class), eq(BinanceTickerResponse[].class)))
                .thenReturn(ResponseEntity.ok(new BinanceTickerResponse[]{
                        new BinanceTickerResponse("BTCUSDT", "93500.10", "4.20", 1713900000000L)
                }));

        List<BinanceTickerResponse> responses = client.fetchTickers(List.of("BTCUSDT"));

        assertThat(responses).singleElement().satisfies(item -> {
            assertThat(item.symbol()).isEqualTo("BTCUSDT");
            assertThat(item.lastPrice()).isEqualTo("93500.10");
        });
    }

    @Test
    void buildsKlinesUriWithoutDoubleEncoding() {
        BinanceClient client = new BinanceClient(restTemplate, properties(), new ObjectMapper());
        when(restTemplate.getForEntity(any(URI.class), eq(Object[][].class)))
                .thenReturn(ResponseEntity.ok(new Object[0][]));

        client.fetchDailyKlines("BTCUSDT", LocalDate.of(2025, 4, 25), LocalDate.of(2026, 4, 24));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(Object[][].class));

        assertThat(uriCaptor.getValue().toString())
                .isEqualTo("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1d&limit=365")
                .doesNotContain("%25");
    }

    @Test
    void mapsKlineArrayResponseToDtoList() {
        BinanceClient client = new BinanceClient(restTemplate, properties(), new ObjectMapper());
        when(restTemplate.getForEntity(any(URI.class), eq(Object[][].class)))
                .thenReturn(ResponseEntity.ok(new Object[][]{
                        {1713830400000L, "92000.00", "94000.00", "91000.00", "93500.10", "1000.00", 1713916799999L}
                }));

        List<BinanceKlineResponse> responses = client.fetchDailyKlines(
                "BTCUSDT",
                LocalDate.of(2025, 4, 25),
                LocalDate.of(2026, 4, 24)
        );

        assertThat(responses).singleElement().satisfies(item -> {
            assertThat(item.openTime()).isEqualTo(1713830400000L);
            assertThat(item.close()).isEqualTo("93500.10");
            assertThat(item.closeTime()).isEqualTo(1713916799999L);
        });
    }

    private BinanceProviderProperties properties() {
        BinanceProviderProperties properties = new BinanceProviderProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.binance.com");
        properties.setSymbols(List.of("BTCUSDT", "ETHUSDT"));
        return properties;
    }
}
