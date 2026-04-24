package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
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

    @Test
    void convertsMultipleSymbolsToSingleEvdsClientCall() {
        EvdsProperties properties = evdsProperties();
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer());
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
        EvdsMarketDataProvider provider = new EvdsMarketDataProvider(evdsClient, properties, mapper, new SymbolNormalizer());

        assertThat(provider.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.EVDS))).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.BINANCE))).isFalse();
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
}
