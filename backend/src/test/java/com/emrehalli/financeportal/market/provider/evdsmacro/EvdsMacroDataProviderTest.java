package com.emrehalli.financeportal.market.provider.evdsmacro;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.EvdsMarketDataMapper;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsItem;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.provider.evdsmacro.config.EvdsMacroProperties;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.MarketBackfillStatusService;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsMacroDataProviderTest {

    @Mock
    private EvdsMacroClient client;

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Mock
    private MarketHistoryService marketHistoryService;

    @Mock
    private MarketBackfillStatusService marketBackfillStatusService;

    @Test
    void requestsMacroEndpointWithParsedFormulaAndMapsSourceToEvdsMacro() {
        EvdsMacroDataProvider provider = provider();
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS_MACRO)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS_MACRO,
                List.of(
                        mapping("TCMBTUFEAYLIK", "TUFE Aylik", "TP.FE25.OKTG01-1"),
                        mapping("TCMBTUFEYILLIK", "TUFE Yillik", "TP.FE25.OKTG01-3")
                )
        ));
        when(marketBackfillStatusService.hasCompletedOneTimeMarker(DataSource.EVDS_MACRO, "EMR:TCMBTUFEAYLIK"))
                .thenReturn(false);
        when(marketBackfillStatusService.hasCompletedOneTimeMarker(DataSource.EVDS_MACRO, "EMR:TCMBTUFEYILLIK"))
                .thenReturn(false);

        EvdsItem item = new EvdsItem();
        item.put("Tarih", "2026-04");
        item.put("TP_FE25_OKTG01", "3.45");
        item.put("TP_FE25_OKTG01_3", "45.67");
        when(client.fetchSeries(
                argThat(requests -> requests.size() == 2
                        && requests.get(0).seriesCode().equals("TP.FE25.OKTG01")
                        && requests.get(0).formula() == 1
                        && requests.get(1).seriesCode().equals("TP.FE25.OKTG01")
                        && requests.get(1).formula() == 3),
                any(),
                any()
        )).thenReturn(new EvdsResponse(List.of(item)));
        when(marketHistoryService.purgeHistoryForSymbol("TCMBTUFEAYLIK")).thenReturn(12L);
        when(marketHistoryService.purgeHistoryForSymbol("TCMBTUFEYILLIK")).thenReturn(14L);

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.EVDS_MACRO));

        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes()).extracting(quote -> quote.symbol())
                .containsExactly("TCMBTUFEAYLIK", "TCMBTUFEYILLIK");
        assertThat(result.historyRecords()).hasSize(2);
        assertThat(result.historyRecords()).extracting(record -> record.source())
                .containsOnly(DataSource.EVDS_MACRO);
        verify(client).fetchSeries(any(), any(), any());
        verify(marketHistoryService).purgeHistoryForSymbol("TCMBTUFEAYLIK");
        verify(marketHistoryService).purgeHistoryForSymbol("TCMBTUFEYILLIK");
        verify(marketBackfillStatusService).markCompletedOneTimeMarker(
                eq(DataSource.EVDS_MACRO),
                eq("EMR:TCMBTUFEAYLIK"),
                eq("Purged legacy market_history rows before first EVDS macro refresh. deletedCount=12")
        );
        verify(marketBackfillStatusService).markCompletedOneTimeMarker(
                eq(DataSource.EVDS_MACRO),
                eq("EMR:TCMBTUFEYILLIK"),
                eq("Purged legacy market_history rows before first EVDS macro refresh. deletedCount=14")
        );
    }

    @Test
    void skipsHistoryResetWhenOneTimeMarkerAlreadyExists() {
        EvdsMacroDataProvider provider = provider();
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS_MACRO)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS_MACRO,
                List.of(mapping("TCMBTUFEYILLIK", "TUFE Yillik", "TP.FE25.OKTG01-3"))
        ));
        when(marketBackfillStatusService.hasCompletedOneTimeMarker(DataSource.EVDS_MACRO, "EMR:TCMBTUFEYILLIK"))
                .thenReturn(true);

        EvdsItem item = new EvdsItem();
        item.put("Tarih", "2026-04");
        item.put("TP_FE25_OKTG01", "45.67");
        when(client.fetchSeries(any(), any(), any())).thenReturn(new EvdsResponse(List.of(item)));

        provider.fetch(ProviderFetchRequest.forSource(DataSource.EVDS_MACRO));

        verify(client).fetchSeries(any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(marketHistoryService);
    }

    @Test
    void supportsOnlyMacroSourceOrUnfilteredRequests() {
        EvdsMacroDataProvider provider = provider();

        assertThat(provider.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.EVDS_MACRO))).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.EVDS))).isFalse();
        assertThat(provider.supports(new ProviderFetchRequest(null, List.of(), java.util.Set.of(InstrumentType.MACRO_INDICATOR), null, null, java.util.Map.of()))).isTrue();
        assertThat(provider.supports(new ProviderFetchRequest(null, List.of(), java.util.Set.of(InstrumentType.FOREX), null, null, java.util.Map.of()))).isFalse();
    }

    private EvdsMacroDataProvider provider() {
        EvdsMacroProperties properties = new EvdsMacroProperties();
        properties.setEnabled(true);
        return new EvdsMacroDataProvider(
                properties,
                client,
                new EvdsMarketDataMapper(),
                new SymbolNormalizer(),
                instrumentRegistryService,
                marketHistoryService,
                marketBackfillStatusService
        );
    }

    private InstrumentRegistryService.ResolvedMapping mapping(String symbol, String displayName, String providerSymbol) {
        return new InstrumentRegistryService.ResolvedMapping(
                java.util.UUID.randomUUID(),
                DataSource.EVDS_MACRO,
                symbol,
                displayName,
                InstrumentType.MACRO_INDICATOR,
                "TRY",
                providerSymbol,
                1,
                360,
                LocalDate.of(2020, 1, 1),
                null,
                com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus.PENDING,
                null,
                null
        );
    }
}
