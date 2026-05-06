package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentRegistryServiceTest {

    @Mock
    private MarketProviderMappingRepository marketProviderMappingRepository;

    @Test
    void usesDbMappingsWhenEnabledMappingsExist() {
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.BINANCE))
                .thenReturn(List.of(mapping(
                        DataSource.BINANCE,
                        "XRPUSDT",
                        true,
                        instrument("XRPUSDT", "Ripple", InstrumentType.CRYPTO, "USDT", true)
                )));

        InstrumentRegistryService service = service(binanceProperties(List.of("BTCUSDT")));

        InstrumentRegistryService.Resolution resolution = service.resolveMappings(DataSource.BINANCE);

        assertThat(resolution.fallbackToYaml()).isFalse();
        assertThat(resolution.mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.symbol()).isEqualTo("XRPUSDT");
            assertThat(mapping.providerSymbol()).isEqualTo("XRPUSDT");
        });
    }

    @Test
    void fallsBackToYamlWhenDbMappingsDoNotExist() {
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.BINANCE))
                .thenReturn(List.of());

        InstrumentRegistryService service = service(binanceProperties(List.of("BTCUSDT", "ETHUSDT")));

        InstrumentRegistryService.Resolution resolution = service.resolveMappings(DataSource.BINANCE);

        assertThat(resolution.fallbackToYaml()).isTrue();
        assertThat(resolution.mappings())
                .extracting(InstrumentRegistryService.ResolvedMapping::providerSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void ignoresDisabledMappingsReturnedFromRepository() {
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.BIST))
                .thenReturn(List.of(
                        mapping(
                                DataSource.BIST,
                                "THYAO.IS",
                                true,
                                instrument("THYAO", "THYAO", InstrumentType.STOCK, "TRY", true)
                        ),
                        mapping(
                                DataSource.BIST,
                                "ASELS.IS",
                                false,
                                instrument("ASELS", "ASELS", InstrumentType.STOCK, "TRY", true)
                        )
                ));

        InstrumentRegistryService service = service(binanceProperties(List.of()));

        InstrumentRegistryService.Resolution resolution = service.resolveMappings(DataSource.BIST);

        assertThat(resolution.fallbackToYaml()).isFalse();
        assertThat(resolution.mappings())
                .extracting(InstrumentRegistryService.ResolvedMapping::providerSymbol)
                .containsExactly("THYAO.IS");
    }

    @Test
    void exposesProviderSpecificMappingLookup() {
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.EVDS))
                .thenReturn(List.of(mapping(
                        DataSource.EVDS,
                        "TP.DK.USD.A",
                        true,
                        instrument("USDTRY", "USD/TRY", InstrumentType.FX, "TRY", true)
                )));
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.BINANCE))
                .thenReturn(List.of());
        when(marketProviderMappingRepository.findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.BIST))
                .thenReturn(List.of());
        InstrumentRegistryService service = service(binanceProperties(List.of()));

        assertThat(service.getByProviderCode(DataSource.EVDS, " tp.dk.usd.a "))
                .isPresent()
                .get()
                .extracting(InstrumentRegistryService.InstrumentDefinition::symbol)
                .isEqualTo("USDTRY");
        verify(marketProviderMappingRepository).findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(DataSource.EVDS);
    }

    private InstrumentRegistryService service(BinanceProviderProperties binanceProviderProperties) {
        BistProviderProperties bistProviderProperties = new BistProviderProperties();
        bistProviderProperties.setSymbols(List.of("THYAO.IS"));

        EvdsProperties evdsProperties = new EvdsProperties();
        EvdsProperties.SeriesConfig usd = new EvdsProperties.SeriesConfig();
        usd.setApiCode("TP.DK.USD.A");
        usd.setEvdsKey("TP_DK_USD_A");
        usd.setSymbol("USDTRY");
        usd.setName("USD/TRY");
        usd.setInstrumentType(InstrumentType.FX);
        usd.setCurrency("TRY");
        evdsProperties.setSeries(List.of(usd));

        return new InstrumentRegistryService(
                new SymbolNormalizer(),
                marketProviderMappingRepository,
                binanceProviderProperties,
                bistProviderProperties,
                evdsProperties
        );
    }

    private BinanceProviderProperties binanceProperties(List<String> symbols) {
        BinanceProviderProperties properties = new BinanceProviderProperties();
        properties.setSymbols(symbols);
        return properties;
    }

    private MarketProviderMappingEntity mapping(DataSource source,
                                                String providerSymbol,
                                                boolean enabled,
                                                MarketInstrumentEntity instrument) {
        MarketProviderMappingEntity entity = new MarketProviderMappingEntity();
        entity.setProviderSource(source);
        entity.setProviderSymbol(providerSymbol);
        entity.setEnabled(enabled);
        entity.setPriority(0);
        entity.setInstrument(instrument);
        return entity;
    }

    private MarketInstrumentEntity instrument(String symbol,
                                              String name,
                                              InstrumentType instrumentType,
                                              String currency,
                                              boolean active) {
        MarketInstrumentEntity entity = new MarketInstrumentEntity();
        entity.setSymbol(symbol);
        entity.setName(name);
        entity.setInstrumentType(instrumentType);
        entity.setCurrency(currency);
        entity.setActive(active);
        return entity;
    }
}
