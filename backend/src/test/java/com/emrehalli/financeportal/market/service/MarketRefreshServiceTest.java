package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketRefreshServiceTest {

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private MarketCacheService marketCacheService;

    @Mock
    private MarketHistoryService marketHistoryService;

    @Test
    void refreshAllWritesSuccessfulSourceCachesAndRebuildsAggregateCache() {
        MarketQuote evdsQuote = quote("USDTRY", DataSource.EVDS);
        MarketQuote binanceQuote = quote("BTCUSDT", DataSource.BINANCE);
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.success(DataSource.EVDS, List.of(evdsQuote)),
                MarketRefreshResult.success(DataSource.BINANCE, List.of(binanceQuote))
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of(evdsQuote, binanceQuote));
        MarketRefreshService service = new MarketRefreshService(providerOrchestrationService, marketCacheService, marketHistoryService);

        List<MarketQuote> refreshedQuotes = service.refreshAll();

        assertThat(refreshedQuotes).containsExactly(evdsQuote, binanceQuote);
        verify(marketCacheService).putSourceQuotes(DataSource.EVDS, List.of(evdsQuote));
        verify(marketCacheService).putSourceQuotes(DataSource.BINANCE, List.of(binanceQuote));
        verify(marketHistoryService).persistHistory(DataSource.EVDS, List.of());
        verify(marketHistoryService).persistHistory(DataSource.BINANCE, List.of());
        verify(marketCacheService).rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
    }

    @Test
    void refreshSourceUpdatesOnlySuccessfulSourceCacheAndRebuildsAggregateFromAllKnownSources() {
        MarketQuote evdsQuote = quote("USDTRY", DataSource.EVDS);
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.success(DataSource.EVDS, List.of(evdsQuote))
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of(evdsQuote));
        MarketRefreshService service = new MarketRefreshService(providerOrchestrationService, marketCacheService, marketHistoryService);

        service.refreshSource(DataSource.EVDS);

        ArgumentCaptor<ProviderFetchRequest> requestCaptor = ArgumentCaptor.forClass(ProviderFetchRequest.class);
        verify(providerOrchestrationService).fetchQuoteResults(requestCaptor.capture());
        assertThat(requestCaptor.getValue().source()).isEqualTo(DataSource.EVDS);
        verify(marketCacheService).putSourceQuotes(DataSource.EVDS, List.of(evdsQuote));
        verify(marketHistoryService).persistHistory(DataSource.EVDS, List.of());
        verify(marketCacheService, never()).putAllQuotes(List.of(evdsQuote));
        verify(marketCacheService).rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
    }

    @Test
    void binanceRefreshRebuildsAggregateCacheWithExistingEvdsSource() {
        MarketQuote binanceQuote = quote("BTCUSDT", DataSource.BINANCE);
        MarketQuote evdsQuote = quote("USDTRY", DataSource.EVDS);
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.success(DataSource.BINANCE, List.of(binanceQuote))
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of(evdsQuote, binanceQuote));
        MarketRefreshService service = new MarketRefreshService(providerOrchestrationService, marketCacheService, marketHistoryService);

        List<MarketQuote> refreshedQuotes = service.refreshSource(DataSource.BINANCE);

        assertThat(refreshedQuotes).containsExactly(binanceQuote);
        verify(marketCacheService).putSourceQuotes(DataSource.BINANCE, List.of(binanceQuote));
        verify(marketCacheService).rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
        verify(marketHistoryService).persistHistory(DataSource.BINANCE, List.of());
    }

    @Test
    void refreshSkipsFailedProviderCacheWritesButStillRebuildsAggregate() {
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.failure(DataSource.EVDS, "timeout")
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of());
        MarketRefreshService service = new MarketRefreshService(providerOrchestrationService, marketCacheService, marketHistoryService);

        List<MarketQuote> refreshedQuotes = service.refreshAll();

        assertThat(refreshedQuotes).isEmpty();
        verify(marketCacheService, never()).putSourceQuotes(any(), any());
        verify(marketHistoryService, never()).persistHistory(any(), any());
        verify(marketCacheService).rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
    }

    private static MarketQuote quote(String symbol, DataSource source) {
        Instant now = Instant.now();
        return new MarketQuote(
                symbol,
                symbol,
                source == DataSource.BINANCE ? InstrumentType.CRYPTO : InstrumentType.FX,
                BigDecimal.ONE,
                null,
                "TRY",
                source,
                now,
                now
        );
    }
}
