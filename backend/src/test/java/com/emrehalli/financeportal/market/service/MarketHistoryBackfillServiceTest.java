package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProperties;
import com.emrehalli.financeportal.market.service.model.BackfillRunStatus;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketHistoryBackfillServiceTest {

    @Mock
    private ProviderOrchestrationService providerOrchestrationService;

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Mock
    private MarketHistoryService marketHistoryService;

    @Mock
    private MarketBackfillStatusService marketBackfillStatusService;

    private MarketHistoryBackfillProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new MarketHistoryBackfillProperties();
        properties.setEnabled(true);
        properties.setMinDataPoints(100);
        properties.setDefaultLookbackDays(365);
        properties.setStartupDelaySeconds(0);
        clock = Clock.fixed(Instant.parse("2026-05-04T00:00:02Z"), ZoneOffset.UTC);
    }

    @Test
    void usesEvdsDefaultBackfillDaysWhenRequestDoesNotProvideOne() {
        EvdsProperties evdsProperties = new EvdsProperties();
        EvdsProperties.History history = new EvdsProperties.History();
        history.setBackfillDefaultDays(365);
        evdsProperties.setHistory(history);
        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                evdsProperties,
                new TefasProperties(),
                properties,
                marketBackfillStatusService,
                clock
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.EVDS, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.EVDS, 0, 0, 0));

        List<MarketHistoryPersistenceResult> results = service.backfill(DataSource.EVDS, null);

        assertThat(service.resolveLookbackDays(DataSource.EVDS, null)).isEqualTo(365);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.source()).isEqualTo(DataSource.EVDS));
        verify(providerOrchestrationService).fetchQuoteResults(any());
    }

    @Test
    void usesDefaultBackfillDaysForBinanceWhenRequestDoesNotProvideOne() {
        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                new EvdsProperties(),
                new TefasProperties(),
                properties,
                marketBackfillStatusService,
                clock
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.BINANCE, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.BINANCE, 0, 0, 0));

        List<MarketHistoryPersistenceResult> results = service.backfill(DataSource.BINANCE, null);

        assertThat(service.resolveLookbackDays(DataSource.BINANCE, null)).isEqualTo(365);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.source()).isEqualTo(DataSource.BINANCE));
        verify(providerOrchestrationService).fetchQuoteResults(any());
    }

    @Test
    void includesRequestedSymbolsWhenBackfillingSpecificInstrument() {
        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                new EvdsProperties(),
                new TefasProperties(),
                properties,
                marketBackfillStatusService,
                clock
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.BINANCE, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.BINANCE, 0, 0, 0));

        service.backfill(DataSource.BINANCE, List.of("BTCUSDT"), 180);

        verify(providerOrchestrationService).fetchQuoteResults(argThat((ProviderFetchRequest request) ->
                request.source() == DataSource.BINANCE
                        && request.symbols().equals(List.of("BTCUSDT"))
        ));
    }

    @Test
    void backfillsWhenHistoryCountBelowThreshold() {
        MarketHistoryBackfillService service = spy(service());
        doNothing().when(service).pauseBetweenChunks();
        mockSingleMapping();
        when(marketHistoryService.countHistory("BTCUSDT", DataSource.BINANCE)).thenReturn(12L, 128L);
        when(marketBackfillStatusService.isEligible(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(12), eq(100), any(), eq(false))).thenReturn(true);
        when(instrumentRegistryService.findPreferredMapping(DataSource.BINANCE, "BTCUSDT"))
                .thenReturn(java.util.Optional.of(mockSingleMappingDefinition()));
        doReturn(365).when(service).resolveLookbackDays(DataSource.BINANCE, null);
        doReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BINANCE, 116, 116, 0)))
                .when(service).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), any(LocalDate.class), any(LocalDate.class));
        when(marketHistoryService.findMinPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(LocalDate.of(2025, 5, 4));
        when(marketHistoryService.findMaxPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.countDistinctClosePrices("BTCUSDT", DataSource.BINANCE)).thenReturn(110L);
        when(marketBackfillStatusService.markCompleted(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(BackfillRunStatus.SUCCESS), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> new MarketBackfillJobResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        0,
                        invocation.getArgument(7)
                ));

        service.markStarted();
        service.runIfDue();

        org.mockito.ArgumentCaptor<LocalDate> startCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.ArgumentCaptor<LocalDate> endCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(service, times(2)).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getAllValues()).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
        assertThat(endCaptor.getAllValues()).containsExactly(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 4));
    }

    @Test
    void usesTefasDefaultBackfillDaysWhenRequestDoesNotProvideOne() {
        TefasProperties tefasProperties = new TefasProperties();
        TefasProperties.History history = new TefasProperties.History();
        history.setBackfillDefaultDays(540);
        tefasProperties.setHistory(history);

        MarketHistoryBackfillService service = new MarketHistoryBackfillService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                new EvdsProperties(),
                tefasProperties,
                properties,
                marketBackfillStatusService,
                clock
        );
        when(providerOrchestrationService.fetchQuoteResults(any()))
                .thenReturn(List.of(MarketRefreshResult.success(DataSource.TEFAS, List.of())));
        when(marketHistoryService.persistHistory(any(), any()))
                .thenReturn(new MarketHistoryPersistenceResult(DataSource.TEFAS, 0, 0, 0));

        List<MarketHistoryPersistenceResult> results = service.backfill(DataSource.TEFAS, null);

        assertThat(service.resolveLookbackDays(DataSource.TEFAS, null)).isEqualTo(540);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.source()).isEqualTo(DataSource.TEFAS));
        verify(providerOrchestrationService).fetchQuoteResults(any());
    }

    @Test
    void skipsBackfillWhenHistoryCountIsAlreadySufficient() {
        MarketHistoryBackfillService service = spy(service());
        doNothing().when(service).pauseBetweenChunks();
        mockSingleMapping();
        when(marketHistoryService.countHistory("BTCUSDT", DataSource.BINANCE)).thenReturn(100L);
        when(marketBackfillStatusService.isEligible(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(100), eq(100), any(), eq(false))).thenReturn(false);

        service.markStarted();
        service.runIfDue();

        verify(service, never()).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void manualTriggerRunsRegardlessOfEligibility() {
        MarketHistoryBackfillService service = spy(service());
        doNothing().when(service).pauseBetweenChunks();
        when(instrumentRegistryService.findPreferredMapping(DataSource.BIST, "THYAO")).thenReturn(java.util.Optional.empty());
        doReturn(120).when(service).resolveLookbackDays(DataSource.BIST, null);
        doReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BIST, 200, 150, 50)))
                .when(service).backfill(eq(DataSource.BIST), eq(List.of("THYAO")), any(LocalDate.class), any(LocalDate.class));
        when(marketHistoryService.countHistory("THYAO", DataSource.BIST)).thenReturn(150L);
        when(marketHistoryService.findMinPriceDate("THYAO", DataSource.BIST)).thenReturn(LocalDate.of(2025, 5, 4));
        when(marketHistoryService.findMaxPriceDate("THYAO", DataSource.BIST)).thenReturn(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.countDistinctClosePrices("THYAO", DataSource.BIST)).thenReturn(120L);
        when(marketBackfillStatusService.markCompleted(eq(DataSource.BIST), eq("THYAO"), eq(BackfillRunStatus.SUCCESS), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> new MarketBackfillJobResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        0,
                        invocation.getArgument(7)
                ));

        service.triggerManual(DataSource.BIST, "THYAO");

        verify(service, times(5)).backfill(eq(DataSource.BIST), eq(List.of("THYAO")), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void chunkFailureDoesNotStopRemainingBackfillChunks() {
        MarketHistoryBackfillService service = spy(service());
        doNothing().when(service).pauseBetweenChunks();
        mockSingleMapping();
        when(marketHistoryService.countHistory("BTCUSDT", DataSource.BINANCE)).thenReturn(12L, 128L);
        when(marketBackfillStatusService.isEligible(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(12), eq(100), any(), eq(false))).thenReturn(true);
        when(instrumentRegistryService.findPreferredMapping(DataSource.BINANCE, "BTCUSDT")).thenReturn(java.util.Optional.of(mockSingleMappingDefinition()));
        doThrow(new RuntimeException("chunk failure"))
                .doReturn(List.of(new MarketHistoryPersistenceResult(DataSource.BINANCE, 50, 50, 0)))
                .when(service).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), any(LocalDate.class), any(LocalDate.class));
        when(marketHistoryService.findMinPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(LocalDate.of(2026, 4, 1));
        when(marketHistoryService.findMaxPriceDate("BTCUSDT", DataSource.BINANCE)).thenReturn(LocalDate.of(2026, 5, 4));
        when(marketHistoryService.countDistinctClosePrices("BTCUSDT", DataSource.BINANCE)).thenReturn(50L);
        when(marketBackfillStatusService.markCompleted(eq(DataSource.BINANCE), eq("BTCUSDT"), eq(BackfillRunStatus.SUCCESS), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> new MarketBackfillJobResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        0,
                        invocation.getArgument(7)
                ));

        service.markStarted();
        service.runIfDue();

        verify(service, times(2)).backfill(eq(DataSource.BINANCE), eq(List.of("BTCUSDT")), any(LocalDate.class), any(LocalDate.class));
        verify(marketBackfillStatusService, atLeastOnce()).updateProgress(DataSource.BINANCE, "BTCUSDT", 2, 1, LocalDate.of(2026, 5, 4));
    }

    private void mockSingleMapping() {
        when(providerOrchestrationService.availableSources()).thenReturn(List.of(DataSource.BINANCE));
        when(instrumentRegistryService.resolveMappings(DataSource.BINANCE)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.BINANCE,
                List.of(mockSingleMappingDefinition())
        ));
    }

    private InstrumentRegistryService.ResolvedMapping mockSingleMappingDefinition() {
        return new InstrumentRegistryService.ResolvedMapping(
                UUID.randomUUID(),
                DataSource.BINANCE,
                "BTCUSDT",
                "Bitcoin",
                com.emrehalli.financeportal.market.domain.enums.InstrumentType.CRYPTO,
                "USDT",
                "BTCUSDT",
                1,
                1,
                LocalDate.of(2026, 1, 1),
                null,
                com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus.PENDING,
                null,
                null
        );
    }

    private MarketHistoryBackfillService service() {
        return new MarketHistoryBackfillService(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                new EvdsProperties(),
                new TefasProperties(),
                properties,
                marketBackfillStatusService,
                clock
        );
    }
}
