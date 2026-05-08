package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Test
    void refreshAllWritesSuccessfulSourceCachesAndRebuildsAggregateCache() {
        MarketQuote evdsQuote = quote("USDTRY", DataSource.EVDS);
        MarketQuote binanceQuote = quote("BTCUSDT", DataSource.BINANCE);
        UUID evdsMappingId = UUID.randomUUID();
        UUID binanceMappingId = UUID.randomUUID();
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.success(DataSource.EVDS, List.of(evdsQuote)),
                MarketRefreshResult.success(DataSource.BINANCE, List.of(binanceQuote))
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of(evdsQuote, binanceQuote));
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(mapping(evdsMappingId, DataSource.EVDS, "USDTRY", "TP.DK.USD.A", InstrumentType.FOREX, "TRY"))
        ));
        when(instrumentRegistryService.resolveMappings(DataSource.BINANCE)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.BINANCE,
                List.of(mapping(binanceMappingId, DataSource.BINANCE, "BTCUSDT", "BTCUSDT", InstrumentType.CRYPTO, "USDT"))
        ));
        MarketRefreshService service = new MarketRefreshService(
                providerOrchestrationService,
                marketCacheService,
                marketHistoryService,
                instrumentRegistryService,
                new SymbolNormalizer()
        );

        List<MarketQuote> refreshedQuotes = service.refreshAll();

        assertThat(refreshedQuotes).containsExactly(evdsQuote, binanceQuote);
        verify(marketCacheService).putSourceQuotes(DataSource.EVDS, List.of(evdsQuote));
        verify(marketCacheService).putSourceQuotes(DataSource.BINANCE, List.of(binanceQuote));
        verify(marketHistoryService).persistHistory(DataSource.EVDS, List.of());
        verify(marketHistoryService).persistHistory(DataSource.BINANCE, List.of());
        verify(marketCacheService).rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
        verify(instrumentRegistryService).markRefreshSuccess(eq(evdsMappingId), any(Instant.class));
        verify(instrumentRegistryService).markRefreshSuccess(eq(binanceMappingId), any(Instant.class));
    }

    @Test
    void refreshSourceUsesSourceRequestAndMarksFailures() {
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.failure(DataSource.EVDS, "timeout")
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of());
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(mapping(UUID.randomUUID(), DataSource.EVDS, "USDTRY", "TP.DK.USD.A", InstrumentType.FOREX, "TRY"))
        ));

        MarketRefreshService service = new MarketRefreshService(
                providerOrchestrationService,
                marketCacheService,
                marketHistoryService,
                instrumentRegistryService,
                new SymbolNormalizer()
        );

        service.refreshSource(DataSource.EVDS);

        ArgumentCaptor<ProviderFetchRequest> requestCaptor = ArgumentCaptor.forClass(ProviderFetchRequest.class);
        verify(providerOrchestrationService).fetchQuoteResults(requestCaptor.capture());
        assertThat(requestCaptor.getValue().source()).isEqualTo(DataSource.EVDS);
        verify(marketCacheService, never()).putSourceQuotes(any(), any());
        verify(instrumentRegistryService).markRefreshFailed(any(UUID.class), eq("timeout"));
    }

    @Test
    void refreshMarksMissingMappingsAsFailedWhenProviderReturnsPartialData() {
        UUID usdMappingId = UUID.randomUUID();
        UUID eurMappingId = UUID.randomUUID();
        MarketQuote usdQuote = quote("USDTRY", DataSource.EVDS);
        when(providerOrchestrationService.fetchQuoteResults(any())).thenReturn(List.of(
                MarketRefreshResult.success(DataSource.EVDS, List.of(usdQuote))
        ));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketCacheService.rebuildAllQuotes(any())).thenReturn(List.of(usdQuote));
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(
                        mapping(usdMappingId, DataSource.EVDS, "USDTRY", "TP.DK.USD.A", InstrumentType.FOREX, "TRY"),
                        mapping(eurMappingId, DataSource.EVDS, "EURTRY", "TP.DK.EUR.A", InstrumentType.FOREX, "TRY")
                )
        ));

        MarketRefreshService service = new MarketRefreshService(
                providerOrchestrationService,
                marketCacheService,
                marketHistoryService,
                instrumentRegistryService,
                new SymbolNormalizer()
        );

        service.refreshSource(DataSource.EVDS);

        verify(instrumentRegistryService).markRefreshSuccess(eq(usdMappingId), any(Instant.class));
        verify(instrumentRegistryService).markRefreshFailed(eurMappingId, "No data returned during refresh");
    }

    private static InstrumentRegistryService.ResolvedMapping mapping(UUID mappingId,
                                                                     DataSource source,
                                                                     String symbol,
                                                                     String providerSymbol,
                                                                     InstrumentType instrumentType,
                                                                     String currency) {
        return new InstrumentRegistryService.ResolvedMapping(
                mappingId,
                source,
                symbol,
                symbol,
                instrumentType,
                currency,
                providerSymbol,
                1,
                15,
                null,
                null,
                com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus.PENDING,
                null,
                null
        );
    }

    private static MarketQuote quote(String symbol, DataSource source) {
        Instant now = Instant.now();
        return new MarketQuote(
                symbol,
                symbol,
                source == DataSource.BINANCE ? InstrumentType.CRYPTO : InstrumentType.FOREX,
                BigDecimal.ONE,
                null,
                source == DataSource.BINANCE ? "USDT" : "TRY",
                source,
                now,
                now,
                MarketPriceStatus.LIVE
        );
    }
}
