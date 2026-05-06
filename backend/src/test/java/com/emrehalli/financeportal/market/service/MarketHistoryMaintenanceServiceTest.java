package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHistoryMaintenanceServiceTest {

    @Mock private ProviderOrchestrationService providerOrchestrationService;
    @Mock private InstrumentRegistryService instrumentRegistryService;
    @Mock private MarketHistoryService marketHistoryService;
    @Mock private MarketHistoryBackfillService marketHistoryBackfillService;
    @Mock private MarketBackfillStatusService marketBackfillStatusService;
    @Mock private Clock clock;

    private MarketHistoryBackfillProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MarketHistoryBackfillProperties();
        properties.setEnabled(true);
        properties.setMinDataPoints(100);
        properties.setDefaultLookbackDays(365);
        properties.setStartupDelaySeconds(0);
    }

    @Test
    void backfillsWhenHistoryCountBelowThreshold() {
        mockSingleMapping();
        when(marketHistoryService.countHistory("BTCUSDT", DataSource.BINANCE)).thenReturn(12L, 128L);
        when(marketBackfillStatusService.isEligible(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(12), eq(100), any(), eq(false))).thenReturn(true);
        when(marketHistoryBackfillService.resolveLookbackDays(DataSource.BINANCE, null)).thenReturn(365);
        when(marketHistoryBackfillService.backfill(DataSource.BINANCE, List.of("BTCUSDT"), 365))
                .thenReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BINANCE, 116, 116, 0)));
        when(marketHistoryService.findMinPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(java.time.LocalDate.of(2025, 5, 4));
        when(marketHistoryService.findMaxPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(java.time.LocalDate.of(2026, 5, 4));
        when(marketHistoryService.countDistinctClosePrices("BTCUSDT", DataSource.BINANCE)).thenReturn(110L);
        when(clock.instant()).thenReturn(
                Instant.parse("2026-05-04T00:00:00Z"),
                Instant.parse("2026-05-04T00:00:02Z")
        );

        MarketHistoryMaintenanceService service = service();
        service.markStarted();
        service.runIfDue();

        verify(marketHistoryBackfillService).backfill(DataSource.BINANCE, List.of("BTCUSDT"), 365);
    }

    @Test
    void skipsBackfillWhenHistoryCountIsAlreadySufficient() {
        mockSingleMapping();
        when(marketHistoryService.countHistory("BTCUSDT", DataSource.BINANCE)).thenReturn(100L);
        when(marketBackfillStatusService.isEligible(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(100), eq(100), any(), eq(false))).thenReturn(false);
        when(clock.instant()).thenReturn(
                Instant.parse("2026-05-04T00:00:00Z"),
                Instant.parse("2026-05-04T00:00:02Z")
        );

        MarketHistoryMaintenanceService service = service();
        service.markStarted();
        service.runIfDue();

        verify(marketHistoryBackfillService, never()).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), anyInt());
    }

    @Test
    void manualTriggerRunsRegardlessOfEligibility() {
        when(marketHistoryBackfillService.resolveLookbackDays(DataSource.BIST, null)).thenReturn(365);
        when(marketHistoryBackfillService.backfill(DataSource.BIST, List.of("THYAO"), 365))
                .thenReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BIST, 200, 150, 50)));
        when(marketHistoryService.countHistory("THYAO", DataSource.BIST)).thenReturn(150L);
        when(marketHistoryService.findMinPriceDate("THYAO", DataSource.BIST)).thenReturn(java.time.LocalDate.of(2025, 5, 4));
        when(marketHistoryService.findMaxPriceDate("THYAO", DataSource.BIST)).thenReturn(java.time.LocalDate.of(2026, 5, 4));
        when(marketHistoryService.countDistinctClosePrices("THYAO", DataSource.BIST)).thenReturn(120L);

        MarketHistoryMaintenanceService service = service();
        service.triggerManual(DataSource.BIST, "THYAO");

        verify(marketHistoryBackfillService).backfill(DataSource.BIST, List.of("THYAO"), 365);
    }

    private void mockSingleMapping() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.BINANCE));
        when(instrumentRegistryService.resolveMappings(DataSource.BINANCE)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.BINANCE,
                List.of(new InstrumentRegistryService.ResolvedMapping(
                        DataSource.BINANCE, "BTCUSDT", "Bitcoin", InstrumentType.CRYPTO, "USDT", "BTCUSDT", 0, null
                )),
                false
        ));
    }

    private MarketHistoryMaintenanceService service() {
        return new MarketHistoryMaintenanceService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                marketHistoryBackfillService,
                properties,
                marketBackfillStatusService,
                clock
        );
    }
}
