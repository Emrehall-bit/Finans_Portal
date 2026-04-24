package com.emrehalli.financeportal.market.provider.binance;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.binance.client.BinanceClient;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceKlineResponse;
import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import com.emrehalli.financeportal.market.provider.binance.mapper.BinanceMapper;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinanceMarketDataProviderTest {

    @Mock
    private BinanceClient binanceClient;

    @Test
    void filtersConfiguredSymbolsAgainstRequest() {
        BinanceMarketDataProvider provider = new BinanceMarketDataProvider(
                binanceClient,
                properties(),
                new BinanceMapper(),
                new SymbolNormalizer()
        );
        when(binanceClient.fetchTickers(any())).thenReturn(List.of(
                new BinanceTickerResponse("BTCUSDT", "93500.10", "4.20", 1713900000000L)
        ));

        provider.fetchQuotes(ProviderFetchRequest.forSymbols(List.of("btc/usdt", "DOGEUSDT")));

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(binanceClient).fetchTickers(symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("BTCUSDT", "DOGEUSDT");
    }

    @Test
    void returnsEmptyListWhenBinanceClientFails() {
        BinanceMarketDataProvider provider = new BinanceMarketDataProvider(
                binanceClient,
                properties(),
                new BinanceMapper(),
                new SymbolNormalizer()
        );
        when(binanceClient.fetchTickers(any())).thenThrow(new IllegalStateException("Binance unavailable"));

        var quotes = provider.fetchQuotes(ProviderFetchRequest.forSource(DataSource.BINANCE));

        assertThat(quotes).isEmpty();
    }

    @Test
    void returnsHistoryRecordsForBackfillRequests() {
        BinanceMarketDataProvider provider = new BinanceMarketDataProvider(
                binanceClient,
                properties(),
                new BinanceMapper(),
                new SymbolNormalizer()
        );
        when(binanceClient.fetchDailyKlines(any(), any(), any())).thenReturn(List.of(
                new BinanceKlineResponse(1713830400000L, "92000.00", "94000.00", "91000.00", "93500.10", "1000.00", 1713916799999L)
        ));

        var result = provider.fetch(new ProviderFetchRequest(
                DataSource.BINANCE,
                List.of("BTCUSDT"),
                java.util.Set.of(),
                LocalDate.of(2025, 4, 25),
                LocalDate.of(2026, 4, 24),
                java.util.Map.of()
        ));

        assertThat(result.quotes()).isEmpty();
        assertThat(result.historyRecords()).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("BTCUSDT");
            assertThat(record.source()).isEqualTo(DataSource.BINANCE);
        });
    }

    private BinanceProviderProperties properties() {
        BinanceProviderProperties properties = new BinanceProviderProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.binance.com");
        properties.setSymbols(List.of("BTCUSDT", "ETHUSDT", "DOGEUSDT"));
        return properties;
    }
}
