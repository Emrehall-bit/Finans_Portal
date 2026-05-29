package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.service.TcmbFxCurrentPriceSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TcmbFxCurrentPriceSchedulerTest {

    private TcmbFxCurrentPriceSyncService tcmbFxCurrentPriceSyncService;
    private MarketProperties marketProperties;
    private TcmbFxCurrentPriceScheduler scheduler;

    @BeforeEach
    void setUp() {
        tcmbFxCurrentPriceSyncService = mock(TcmbFxCurrentPriceSyncService.class);
        marketProperties = new MarketProperties();
        scheduler = new TcmbFxCurrentPriceScheduler(tcmbFxCurrentPriceSyncService, marketProperties);
    }

    @Test
    void syncSkipsWhenDisabled() {
        marketProperties.getProviders().getTcmb().setCurrentSyncEnabled(false);

        scheduler.sync();

        verify(tcmbFxCurrentPriceSyncService, never()).syncCurrentPrices();
    }

    @Test
    void syncRunsWhenEnabled() {
        marketProperties.getProviders().getTcmb().setCurrentSyncEnabled(true);

        scheduler.sync();

        verify(tcmbFxCurrentPriceSyncService).syncCurrentPrices();
    }
}




