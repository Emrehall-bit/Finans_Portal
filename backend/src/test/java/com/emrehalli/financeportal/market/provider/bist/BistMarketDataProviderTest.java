package com.emrehalli.financeportal.market.provider.bist;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.bist.client.BistDelayedClient;
import com.emrehalli.financeportal.market.provider.bist.client.YahooClient;
import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import com.emrehalli.financeportal.market.provider.bist.mapper.BistMapper;
import com.emrehalli.financeportal.market.provider.bist.support.BistRoundRobinState;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BistMarketDataProviderTest {

    @Mock
    private YahooClient yahooClient;

    @Mock
    private BistDelayedClient delayedClient;

    @Test
    void fetchesOnlyCurrentRoundRobinBatchFromYahoo() {
        BistMarketDataProvider provider = provider(properties(), new BistRoundRobinState());
        when(yahooClient.fetchQuotes(any())).thenReturn(YahooClient.FetchResult.success(List.of(
                new BistQuoteResponse("THYAO.IS", "THYAO", null, new BigDecimal("320.40"), null, 1777032000L),
                new BistQuoteResponse("ASELS.IS", "ASELS", null, new BigDecimal("145.20"), null, 1777032000L)
        )));

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(yahooClient).fetchQuotes(symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("THYAO.IS", "ASELS.IS");
        assertThat(result.quotes()).hasSize(2);
        assertThat(result.historyRecords()).hasSize(2);
    }

    @Test
    void rateLimitedYahooStartsCooldownAndKeepsCursor() {
        BistRoundRobinState state = new BistRoundRobinState();
        BistMarketDataProvider provider = provider(properties(), state);
        when(yahooClient.fetchQuotes(any()))
                .thenReturn(YahooClient.FetchResult.rateLimited(429));

        provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));
        provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(yahooClient, org.mockito.Mockito.times(1)).fetchQuotes(symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("THYAO.IS", "ASELS.IS");
    }

    @Test
    void usesFallbackWhenYahooReturnsEmptyAndDelayedEnabled() {
        BistProviderProperties properties = properties();
        properties.getDelayed().setEnabled(true);
        BistMarketDataProvider provider = provider(properties, new BistRoundRobinState());
        when(yahooClient.fetchQuotes(any())).thenReturn(YahooClient.FetchResult.success(List.of()));
        when(delayedClient.fetchQuotes(any())).thenReturn(List.of(
                new BistQuoteResponse("THYAO.IS", "THYAO", null, new BigDecimal("320.40"), null, 1777032000L)
        ));

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));

        verify(delayedClient).fetchQuotes(List.of("THYAO.IS", "ASELS.IS"));
        assertThat(result.quotes()).singleElement().satisfies(quote -> assertThat(quote.symbol()).isEqualTo("THYAO"));
    }

    @Test
    void usesFallbackDuringYahooCooldownWhenDelayedEnabled() {
        BistProviderProperties properties = properties();
        properties.getDelayed().setEnabled(true);
        BistRoundRobinState state = new BistRoundRobinState();
        BistMarketDataProvider provider = provider(properties, state);
        when(yahooClient.fetchQuotes(any()))
                .thenReturn(YahooClient.FetchResult.rateLimited(429));
        when(delayedClient.fetchQuotes(any())).thenReturn(List.of(
                new BistQuoteResponse("THYAO.IS", "THYAO", null, new BigDecimal("320.40"), null, 1777032000L)
        ));

        provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));
        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));

        verify(delayedClient).fetchQuotes(List.of("THYAO.IS", "ASELS.IS"));
        assertThat(result.quotes()).singleElement().satisfies(quote -> assertThat(quote.symbol()).isEqualTo("THYAO"));
    }

    @Test
    void unauthorizedYahooKeepsExistingCacheByReturningEmpty() {
        BistMarketDataProvider provider = provider(properties(), new BistRoundRobinState());
        when(yahooClient.fetchQuotes(any())).thenReturn(YahooClient.FetchResult.unauthorized(401));

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.BIST));

        verify(delayedClient, never()).fetchQuotes(any());
        assertThat(result.quotes()).isEmpty();
    }

    private BistMarketDataProvider provider(BistProviderProperties properties, BistRoundRobinState state) {
        return new BistMarketDataProvider(
                yahooClient,
                delayedClient,
                properties,
                new BistMapper(),
                new SymbolNormalizer(),
                state
        );
    }

    private BistProviderProperties properties() {
        BistProviderProperties properties = new BistProviderProperties();
        properties.setEnabled(true);
        properties.setProviderMode("YAHOO_LOW_FREQUENCY");
        properties.setBatchSize(2);
        properties.setRequestDelayMs(0L);
        properties.setCooldownMinutesOnRateLimit(60);
        properties.setFallbackSource("BIST_DELAYED");
        properties.setSymbols(List.of("THYAO.IS", "ASELS.IS", "GARAN.IS", "AKBNK.IS"));
        properties.getYahoo().setEnabled(true);
        properties.getYahoo().setBaseUrl("https://query1.finance.yahoo.com");
        properties.getDelayed().setEnabled(false);
        return properties;
    }
}
