package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsMarketDataProviderTest {

    @Mock
    private EvdsMarketDataMapper mapper;

    @Mock
    private InstrumentRegistryService instrumentRegistryService;

    @Mock
    private EvdsBatchExecutor batchExecutor;

    @Test
    void preservesProviderSeriesCodesInBatchRequests() {
        EvdsMarketDataProvider provider = provider();
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(
                        mapping("TCMBTUFEAYLIK", "TUFE Aylik Degisim (%)", InstrumentType.MACRO_INDICATOR, "TRY", "TP_TUKFIY2025_GENEL-1"),
                        mapping("TCMBTUFEYILLIK", "TUFE Yillik Degisim (%)", InstrumentType.MACRO_INDICATOR, "TRY", "TP_TUKFIY2025_GENEL-3")
                )
        ));
        when(batchExecutor.execute(any(), any(), any())).thenReturn(new EvdsBatchExecutor.ExecutionResult(List.of(), List.of(), 5, 0));

        provider.fetchQuotes(ProviderFetchRequest.forSymbols(List.of("TCMBTUFE_AYLIK", "TCMBTUFE_YILLIK")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvdsRequestBuilder.EvdsSeriesRequest>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchExecutor).execute(requestCaptor.capture(), any(), any());
        assertThat(requestCaptor.getValue())
                .extracting(EvdsRequestBuilder.EvdsSeriesRequest::requestSeriesCode)
                .containsExactly("TP_TUKFIY2025_GENEL-1", "TP_TUKFIY2025_GENEL-3");
    }

    @Test
    void skipsInvalidSeriesWithoutFailingWholeRefresh() {
        EvdsMarketDataProvider provider = provider();
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(
                        mapping("VALID", "Valid", InstrumentType.MACRO_INDICATOR, "TRY", "TP.TIG08"),
                        mapping("INVALID", "Invalid", InstrumentType.MACRO_INDICATOR, "TRY", "   ")
                )
        ));
        when(batchExecutor.execute(any(), any(), any())).thenReturn(new EvdsBatchExecutor.ExecutionResult(List.of(), List.of(), 5, 0));

        ProviderFetchResult result = provider.fetch(ProviderFetchRequest.all());

        assertThat(result.quotes()).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvdsRequestBuilder.EvdsSeriesRequest>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchExecutor).execute(requestCaptor.capture(), any(), any());
        assertThat(requestCaptor.getValue()).singleElement().satisfies(request ->
                assertThat(request.originalSeriesCode()).isEqualTo("TP.TIG08")
        );
    }

    @Test
    void mapsPartialSuccessPayloadsWithoutFailingWholeRefresh() {
        EvdsMarketDataProvider provider = provider();
        when(instrumentRegistryService.resolveMappings(DataSource.EVDS)).thenReturn(new InstrumentRegistryService.Resolution(
                DataSource.EVDS,
                List.of(
                        mapping("TCMBISSIZLIK", "Issizlik Orani (%)", InstrumentType.MACRO_INDICATOR, "TRY", "TP.TIG08"),
                        mapping("TCMBISSIZLIKD", "Issizlik Degisim (%)", InstrumentType.MACRO_INDICATOR, "TRY", "TP.TIG08-1")
                )
        ));
        EvdsProperties.SeriesConfig seriesConfig = new EvdsProperties.SeriesConfig();
        seriesConfig.setSymbol("TCMBISSIZLIK");
        seriesConfig.setName("Issizlik Orani (%)");
        seriesConfig.setInstrumentType(InstrumentType.MACRO_INDICATOR);
        seriesConfig.setCurrency("TRY");
        seriesConfig.setApiCode("TP.TIG08");
        seriesConfig.setEvdsKey("TP.TIG08");

        when(batchExecutor.execute(any(), any(), any())).thenReturn(new EvdsBatchExecutor.ExecutionResult(
                List.of(new EvdsBatchExecutor.SuccessfulPayload(new EvdsResponse(List.of()), List.of(seriesConfig), List.of("TP.TIG08"))),
                List.of("TP.TIG08-1"),
                5,
                1
        ));
        when(mapper.toMarketQuotes(any(), any())).thenReturn(List.of(new MarketQuote(
                "TCMBISSIZLIK",
                "Issizlik Orani (%)",
                InstrumentType.MACRO_INDICATOR,
                BigDecimal.ONE,
                null,
                "TRY",
                DataSource.EVDS,
                Instant.parse("2026-05-07T00:00:00Z"),
                Instant.parse("2026-05-07T00:00:00Z"),
                MarketPriceStatus.LIVE
        )));
        when(mapper.toHistoryRecords(any(), any())).thenReturn(List.of(new MarketHistoryRecord(
                "TCMBISSIZLIK",
                "Issizlik Orani (%)",
                InstrumentType.MACRO_INDICATOR,
                DataSource.EVDS,
                LocalDate.of(2026, 5, 7),
                BigDecimal.ONE,
                "TRY"
        )));

        ProviderFetchResult result = provider.fetch(ProviderFetchRequest.all());

        assertThat(result.quotes()).hasSize(1);
        verify(mapper).toMarketQuotes(any(), any());
        verify(mapper).toHistoryRecords(any(), any());
    }

    @Test
    void supportsOnlyEvdsOrUnfilteredRequests() {
        EvdsMarketDataProvider provider = provider();

        assertThat(provider.supports(ProviderFetchRequest.all())).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.EVDS))).isTrue();
        assertThat(provider.supports(ProviderFetchRequest.forSource(DataSource.BINANCE))).isFalse();
    }

    private EvdsMarketDataProvider provider() {
        return new EvdsMarketDataProvider(
                evdsProperties(),
                mapper,
                new SymbolNormalizer(),
                instrumentRegistryService,
                new EvdsRequestBuilder(new EvdsSeriesValidator(new EvdsSeriesNormalizer())),
                batchExecutor
        );
    }

    private EvdsProperties evdsProperties() {
        EvdsProperties properties = new EvdsProperties();
        properties.setEnabled(true);
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
