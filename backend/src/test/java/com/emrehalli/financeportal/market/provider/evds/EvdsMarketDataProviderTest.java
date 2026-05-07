package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsMarketDataProviderTest {

    @Mock
    private EvdsClient evdsClient;

    @Mock
    private EvdsMarketDataMapper mapper;

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Test
    void convertsMultipleSymbolsToSingleEvdsClientCall() {
        EvdsProperties properties = evdsProperties();
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer(), instrumentRegistryService);
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(
                        mapping("USDTRY", "USD/TRY", InstrumentType.FOREX, "TRY", "TP.DK.USD.A"),
                        mapping("EURTRY", "EUR/TRY", InstrumentType.FOREX, "TRY", "TP.DK.EUR.A")
                )
        ));
        when(evdsClient.fetchSeries(any(), any(), any())).thenReturn(new EvdsResponse(List.of()));
        when(mapper.toMarketQuotes(any(), any())).thenReturn(List.of());

        provider.fetchQuotes(ProviderFetchRequest.forSymbols(List.of("usdtry", "EUR/TRY")));

        ArgumentCaptor<List<String>> seriesCodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(evdsClient).fetchSeries(seriesCodesCaptor.capture(), any(), any());
        assertThat(seriesCodesCaptor.getValue())
                .containsExactlyInAnyOrder("TP.DK.USD.A", "TP.DK.EUR.A");
    }

    @Test
    void supportsOnlyEvdsOrUnfilteredRequests() {
        EvdsProperties properties = evdsProperties();
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer(), instrumentRegistryService);

        assertThat(provider.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.EVDS))).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.BINANCE))).isFalse();
    }

    @Test
    void buildsCommoditySeriesConfigFromRegistryMapping() {
        EvdsProperties properties = evdsProperties();
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer(), instrumentRegistryService);
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(mapping("XAUTRY", "Gram Altin", InstrumentType.COMMODITY, "TRY", "TP.MK.ALTIN.GRM"))
        ));
        when(evdsClient.fetchSeries(any(), any(), any())).thenReturn(new EvdsResponse(List.of()));
        when(mapper.toMarketQuotes(any(), any())).thenReturn(List.of());
        when(mapper.toHistoryRecords(any(), any())).thenReturn(List.of());

        provider.fetch(ProviderFetchRequest.forSymbols(List.of("XAUTRY")));

        ArgumentCaptor<List<EvdsProperties.SeriesConfig>> seriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).toMarketQuotes(any(), seriesCaptor.capture());
        assertThat(seriesCaptor.getValue()).singleElement().satisfies(series -> {
            assertThat(series.getSymbol()).isEqualTo("XAUTRY");
            assertThat(series.getInstrumentType()).isEqualTo(InstrumentType.COMMODITY);
            assertThat(series.getApiCode()).isEqualTo("TP.MK.ALTIN.GRM");
            assertThat(series.getEvdsKey()).isEqualTo("TP.MK.ALTIN.GRM");
            assertThat(series.getCurrency()).isEqualTo("TRY");
        });
    }

    @Test
    void buildsMacroIndicatorSeriesConfigFromRegistryMapping() {
        EvdsProperties properties = evdsProperties();
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer(), instrumentRegistryService);
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(mapping("TCMBTUFE", "TCMB TUFE", InstrumentType.MACRO_INDICATOR, "TRY", "TP.FG.J0"))
        ));
        when(evdsClient.fetchSeries(any(), any(), any())).thenReturn(new EvdsResponse(List.of()));
        when(mapper.toMarketQuotes(any(), any())).thenReturn(List.of());
        when(mapper.toHistoryRecords(any(), any())).thenReturn(List.of());

        provider.fetch(ProviderFetchRequest.forSymbols(List.of("TCMB_TUFE")));

        ArgumentCaptor<List<EvdsProperties.SeriesConfig>> seriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).toMarketQuotes(any(), seriesCaptor.capture());
        assertThat(seriesCaptor.getValue()).singleElement().satisfies(series -> {
            assertThat(series.getSymbol()).isEqualTo("TCMBTUFE");
            assertThat(series.getInstrumentType()).isEqualTo(InstrumentType.MACRO_INDICATOR);
            assertThat(series.getApiCode()).isEqualTo("TP.FG.J0");
            assertThat(series.getEvdsKey()).isEqualTo("TP.FG.J0");
            assertThat(series.getCurrency()).isEqualTo("TRY");
        });
    }

    private EvdsProperties evdsProperties() {
        EvdsProperties properties = new EvdsProperties();
        EvdsProperties.SeriesConfig usd = new EvdsProperties.SeriesConfig();
        usd.setEvdsKey("TP_DK_USD_A");
        usd.setApiCode("TP.DK.USD.A");
        usd.setSymbol("USDTRY");

        EvdsProperties.SeriesConfig eur = new EvdsProperties.SeriesConfig();
        eur.setEvdsKey("TP_DK_EUR_A");
        eur.setApiCode("TP.DK.EUR.A");
        eur.setSymbol("EURTRY");

        properties.setEnabled(true);
        properties.setSeries(List.of(usd, eur));
        return properties;
    }

    private InstrumentRegistryService.ResolvedMapping mapping(String symbol,
                                                              String displayName,
                                                              InstrumentType instrumentType,
                                                              String currency,
                                                              String providerSymbol) {
        return new InstrumentRegistryService.ResolvedMapping(
                java.util.UUID.randomUUID(),
                DataSource.EVDS,
                symbol,
                displayName,
                instrumentType,
                currency,
                providerSymbol,
                1,
                5,
                null,
                null,
                com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus.PENDING,
                null,
                null
        );
    }
}
