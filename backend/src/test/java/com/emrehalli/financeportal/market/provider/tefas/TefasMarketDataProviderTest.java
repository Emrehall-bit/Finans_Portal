package com.emrehalli.financeportal.market.provider.tefas;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.tefas.client.TefasClient;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProviderProperties;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import com.emrehalli.financeportal.market.provider.tefas.mapper.TefasMapper;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TefasMarketDataProviderTest {

    @Mock
    private TefasClient tefasClient;

    @Test
    void supportsOnlyTefasOrUnfilteredFundRequests() {
        TefasMarketDataProvider provider = new TefasMarketDataProvider(
                tefasClient,
                properties(),
                new TefasMapper(),
                new SymbolNormalizer()
        );

        assertThat(provider.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.TEFAS))).isTrue();
        assertThat(provider.supports(new ProviderFetchRequest(null, List.of(), Set.of(InstrumentType.FUND), null, null, java.util.Map.of()))).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.BINANCE))).isFalse();
    }

    @Test
    void filtersConfiguredSymbolsAgainstRequest() {
        TefasMarketDataProvider provider = new TefasMarketDataProvider(
                tefasClient,
                properties(),
                new TefasMapper(),
                new SymbolNormalizer()
        );
        when(tefasClient.fetchFunds(any())).thenReturn(List.of(
                new TefasFundResponse("AFT", "AK PORTFOY ALTIN FONU", "12,345678", "1,2345", LocalDate.of(2026, 4, 24))
        ));

        provider.fetchQuotes(ProviderFetchRequest.forSymbols(List.of("aft", "xyz")));

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tefasClient).fetchFunds(symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("AFT");
    }

    @Test
    void returnsHistoryRecordsForDailyFundData() {
        TefasMarketDataProvider provider = new TefasMarketDataProvider(
                tefasClient,
                properties(),
                new TefasMapper(),
                new SymbolNormalizer()
        );
        when(tefasClient.fetchFunds(any())).thenReturn(List.of(
                new TefasFundResponse("AFT", "AK PORTFOY ALTIN FONU", "12,345678", "1,2345", LocalDate.of(2026, 4, 24))
        ));

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.TEFAS));

        assertThat(result.quotes()).singleElement().satisfies(quote -> assertThat(quote.symbol()).isEqualTo("AFT"));
        assertThat(result.historyRecords()).singleElement().satisfies(record -> {
            assertThat(record.symbol()).isEqualTo("AFT");
            assertThat(record.source()).isEqualTo(DataSource.TEFAS);
        });
    }

    @Test
    void returnsEmptyResultWhenClientFails() {
        TefasMarketDataProvider provider = new TefasMarketDataProvider(
                tefasClient,
                properties(),
                new TefasMapper(),
                new SymbolNormalizer()
        );
        when(tefasClient.fetchFunds(any())).thenThrow(new IllegalStateException("TEFAS unavailable"));

        var result = provider.fetch(ProviderFetchRequest.forSource(DataSource.TEFAS));

        assertThat(result.quotes()).isEmpty();
        assertThat(result.historyRecords()).isEmpty();
    }

    private TefasProviderProperties properties() {
        TefasProviderProperties properties = new TefasProviderProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://www.tefas.gov.tr");
        properties.setSymbols(List.of("AFT", "IIH", "TCD"));
        return properties;
    }
}
