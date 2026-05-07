package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillService;
import com.emrehalli.financeportal.market.service.MarketRefreshService;
import com.emrehalli.financeportal.market.service.ProviderOrchestrationService;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketRefreshSchedulerTest {

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private MarketRefreshService marketRefreshService;

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Mock
    private MarketHistoryBackfillService marketHistoryBackfillService;

    private MarketRefreshProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new MarketRefreshProperties();
        MarketRefreshProperties.ProviderPolicy evdsPolicy = new MarketRefreshProperties.ProviderPolicy();
        evdsPolicy.setEnabled(true);
        MarketRefreshProperties.ProviderPolicy binancePolicy = new MarketRefreshProperties.ProviderPolicy();
        binancePolicy.setEnabled(true);
        properties.setProviders(Map.of("evds", evdsPolicy, "binance", binancePolicy));
        clock = Clock.fixed(Instant.parse("2026-04-23T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void dueMappingsAreRefreshedBySourceAndSymbolSubset() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(instrumentRegistryService.getDueMappings(DataSource.EVDS, clock.instant())).thenReturn(List.of(
                mapping(DataSource.EVDS, "USDTRY", "TP.DK.USD.A"),
                mapping(DataSource.EVDS, "EURTRY", "TP.DK.EUR.A")
        ));
        when(marketRefreshService.refreshDetailed(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.EVDS, List.of())));

        MarketRefreshScheduler scheduler = new MarketRefreshScheduler(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                instrumentRegistryService,
                marketHistoryBackfillService,
                clock
        );

        scheduler.refreshDueProviders();

        ArgumentCaptor<ProviderFetchRequest> requestCaptor = ArgumentCaptor.forClass(ProviderFetchRequest.class);
        verify(marketRefreshService).refreshDetailed(requestCaptor.capture());
        assertThat(requestCaptor.getValue().source()).isEqualTo(DataSource.EVDS);
        assertThat(requestCaptor.getValue().symbols()).containsExactly("USDTRY", "EURTRY");
    }

    @Test
    void sourceWithoutDueMappingsIsSkipped() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(instrumentRegistryService.getDueMappings(DataSource.EVDS, clock.instant())).thenReturn(List.of());

        MarketRefreshScheduler scheduler = new MarketRefreshScheduler(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                instrumentRegistryService,
                marketHistoryBackfillService,
                clock
        );

        scheduler.refreshDueProviders();

        verify(marketRefreshService, never()).refreshDetailed(org.mockito.ArgumentMatchers.any());
    }

    private InstrumentRegistryService.ResolvedMapping mapping(DataSource source, String symbol, String providerSymbol) {
        return new InstrumentRegistryService.ResolvedMapping(
                UUID.randomUUID(),
                source,
                symbol,
                symbol,
                InstrumentType.FOREX,
                "TRY",
                providerSymbol,
                1,
                15,
                LocalDate.of(2000, 1, 1),
                null,
                MappingRefreshStatus.PENDING,
                null,
                null
        );
    }
}
