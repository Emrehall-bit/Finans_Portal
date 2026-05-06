package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketOverviewServiceTest {

    @Mock
    private MarketCacheService marketCacheService;

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private MarketRefreshService marketRefreshService;

    @Test
    void getByTypeReturnsQuotesFromAggregateCache() {
        MarketQuote evdsQuote = quote("USDTRY", InstrumentType.FX, DataSource.EVDS, "38.12");
        when(marketCacheService.getAllQuotes()).thenReturn(List.of(evdsQuote));

        MarketOverviewService service = new MarketOverviewService(
                marketCacheService,
                providerOrchestrationService,
                marketRefreshService
        );

        List<MarketQuote> result = service.getByType(InstrumentType.CURRENCY);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.symbol()).isEqualTo("USDTRY");
            assertThat(dto.instrumentType()).isEqualTo(InstrumentType.FX);
            assertThat(dto.source()).isEqualTo(DataSource.EVDS);
        });
        verify(marketRefreshService, never()).refreshSource(DataSource.EVDS);
    }

    @Test
    void getByTypeRefreshesMappedSourceWhenCacheIsEmpty() {
        MarketQuote binanceQuote = quote("BTCUSDT", InstrumentType.CRYPTO, DataSource.BINANCE, "95000.00");
        when(marketCacheService.getAllQuotes()).thenReturn(List.of(), List.of(binanceQuote));
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.BINANCE));
        when(marketCacheService.rebuildAllQuotes(List.of(DataSource.BINANCE))).thenReturn(List.of(), List.of(binanceQuote));

        MarketOverviewService service = new MarketOverviewService(
                marketCacheService,
                providerOrchestrationService,
                marketRefreshService
        );

        List<MarketQuote> result = service.getByType(InstrumentType.CRYPTO);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.symbol()).isEqualTo("BTCUSDT");
            assertThat(dto.instrumentType()).isEqualTo(InstrumentType.CRYPTO);
            assertThat(dto.source()).isEqualTo(DataSource.BINANCE);
        });
        verify(marketRefreshService).refreshSource(DataSource.BINANCE);
    }

    private static MarketQuote quote(String symbol,
                                     InstrumentType instrumentType,
                                     DataSource source,
                                     String price) {
        Instant now = Instant.parse("2026-05-04T10:00:00Z");
        return new MarketQuote(
                symbol,
                symbol,
                instrumentType,
                new BigDecimal(price),
                null,
                "TRY",
                source,
                now,
                now
        );
    }
}
