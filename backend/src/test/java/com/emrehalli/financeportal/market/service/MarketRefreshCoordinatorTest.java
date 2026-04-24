package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketRefreshCoordinatorTest {

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private MarketRefreshService marketRefreshService;

    private MarketRefreshProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new MarketRefreshProperties();
        MarketRefreshProperties.ProviderPolicy evdsPolicy = new MarketRefreshProperties.ProviderPolicy();
        evdsPolicy.setEnabled(true);
        evdsPolicy.setRefreshMinutes(15);

        MarketRefreshProperties.ProviderPolicy binancePolicy = new MarketRefreshProperties.ProviderPolicy();
        binancePolicy.setEnabled(true);
        binancePolicy.setRefreshMinutes(5);

        properties.setProviders(Map.of(
                "evds", evdsPolicy,
                "binance", binancePolicy
        ));
        clock = Clock.fixed(Instant.parse("2026-04-23T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void dueProvidersAreRefreshed() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketRefreshService.refreshSourceDetailed(DataSource.EVDS))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.EVDS, List.of())));
        MarketRefreshCoordinator coordinator = new MarketRefreshCoordinator(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                clock
        );

        coordinator.refreshDueProviders();

        verify(marketRefreshService).refreshSourceDetailed(DataSource.EVDS);
    }

    @Test
    void notDueProvidersAreSkipped() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketRefreshService.refreshSourceDetailed(DataSource.EVDS))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.EVDS, List.of())));
        MarketRefreshCoordinator coordinator = new MarketRefreshCoordinator(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                clock
        );

        coordinator.refreshDueProviders();
        coordinator.refreshDueProviders();

        verify(marketRefreshService, times(1)).refreshSourceDetailed(DataSource.EVDS);
    }

    @Test
    void failedProviderResultIsRetriedOnNextTickBecauseTimestampIsNotUpdated() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketRefreshService.refreshSourceDetailed(DataSource.EVDS))
                .thenReturn(List.of(MarketRefreshResult.failure(DataSource.EVDS, "EVDS unavailable")));
        MarketRefreshCoordinator coordinator = new MarketRefreshCoordinator(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                clock
        );

        coordinator.refreshDueProviders();
        coordinator.refreshDueProviders();

        verify(marketRefreshService, times(2)).refreshSourceDetailed(DataSource.EVDS);
    }

    @Test
    void exceptionDuringRefreshIsRetriedOnNextTickBecauseTimestampIsNotUpdated() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS));
        when(marketRefreshService.refreshSourceDetailed(DataSource.EVDS))
                .thenThrow(new IllegalStateException("EVDS unavailable"));
        MarketRefreshCoordinator coordinator = new MarketRefreshCoordinator(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                clock
        );

        coordinator.refreshDueProviders();
        coordinator.refreshDueProviders();

        verify(marketRefreshService, times(2)).refreshSourceDetailed(DataSource.EVDS);
    }

    @Test
    void oneProviderFailureDoesNotBlockOtherDueProviders() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.EVDS, DataSource.BINANCE));
        when(marketRefreshService.refreshSourceDetailed(DataSource.EVDS))
                .thenThrow(new IllegalStateException("EVDS unavailable"));
        when(marketRefreshService.refreshSourceDetailed(DataSource.BINANCE))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.BINANCE, List.of())));
        MarketRefreshCoordinator coordinator = new MarketRefreshCoordinator(
                providerOrchestrationService,
                marketRefreshService,
                properties,
                clock
        );

        coordinator.refreshDueProviders();

        verify(marketRefreshService).refreshSourceDetailed(DataSource.EVDS);
        verify(marketRefreshService).refreshSourceDetailed(DataSource.BINANCE);
    }
}
