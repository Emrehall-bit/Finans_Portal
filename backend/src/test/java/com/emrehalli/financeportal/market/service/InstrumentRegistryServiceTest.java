package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentRegistryServiceTest {

    @Mock
    private MarketInstrumentRepository marketInstrumentRepository;

    @Mock
    private MarketProviderMappingRepository marketProviderMappingRepository;

    @Test
    void resolvesDbMappingsForSource() {
        when(marketProviderMappingRepository.findBySourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(DataSource.BINANCE))
                .thenReturn(List.of(mapping(
                        DataSource.BINANCE,
                        "XRPUSDT",
                        instrument("XRPUSDT", "Ripple", InstrumentType.CRYPTO, true)
                )));

        InstrumentRegistryService service = service();

        InstrumentRegistryService.Resolution resolution = service.resolveMappings(DataSource.BINANCE);

        assertThat(resolution.mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.symbol()).isEqualTo("XRPUSDT");
            assertThat(mapping.providerSymbol()).isEqualTo("XRPUSDT");
            assertThat(mapping.refreshIntervalMinutes()).isEqualTo(5);
        });
    }

    @Test
    void exposesProviderSpecificLookup() {
        MarketProviderMappingEntity mapping = mapping(
                DataSource.EVDS,
                "TP.DK.USD.A",
                instrument("USDTRY", "USD/TRY", InstrumentType.FOREX, true)
        );
        when(marketProviderMappingRepository.findBySourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(DataSource.EVDS))
                .thenReturn(List.of(mapping));
        when(marketProviderMappingRepository.findByEnabledTrueAndInstrument_EnabledTrueOrderBySourceAscPriorityAscIdAsc())
                .thenReturn(List.of(mapping));

        InstrumentRegistryService service = service();

        assertThat(service.getByProviderCode(DataSource.EVDS, "tp.dk.usd.a"))
                .isPresent()
                .get()
                .extracting(InstrumentRegistryService.InstrumentDefinition::symbol)
                .isEqualTo("USDTRY");
    }

    @Test
    void listsDueMappingsFromRepository() {
        when(marketProviderMappingRepository.findDueMappings(any()))
                .thenReturn(List.of(mapping(
                        DataSource.BIST,
                        "THYAO.IS",
                        instrument("THYAO", "Turk Hava Yollari", InstrumentType.STOCK, true)
                )));

        InstrumentRegistryService service = service();

        assertThat(service.getDueMappings(Instant.parse("2026-05-06T00:00:00Z")))
                .singleElement()
                .satisfies(mapping -> assertThat(mapping.symbol()).isEqualTo("THYAO"));
    }

    @Test
    void marksRefreshSuccessOnPreferredMapping() {
        MarketProviderMappingEntity entity = mapping(
                DataSource.BINANCE,
                "BTCUSDT",
                instrument("BTCUSDT", "Bitcoin", InstrumentType.CRYPTO, true)
        );
        when(marketProviderMappingRepository.findFirstByInstrument_SymbolAndSourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(
                "BTCUSDT",
                DataSource.BINANCE
        )).thenReturn(Optional.of(entity));

        InstrumentRegistryService service = service();
        Instant refreshedAt = Instant.parse("2026-05-06T00:00:00Z");

        service.markRefreshSuccess(DataSource.BINANCE, java.util.Set.of("BTCUSDT"), refreshedAt);

        assertThat(entity.getLastRefreshStatus()).isEqualTo(MappingRefreshStatus.SUCCESS);
        assertThat(entity.getLastRefreshedAt()).isEqualTo(refreshedAt);
        verify(marketProviderMappingRepository).save(entity);
    }

    private InstrumentRegistryService service() {
        return new InstrumentRegistryService(
                new SymbolNormalizer(),
                marketInstrumentRepository,
                marketProviderMappingRepository,
                Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private MarketProviderMappingEntity mapping(DataSource source,
                                                String externalSymbol,
                                                MarketInstrumentEntity instrument) {
        MarketProviderMappingEntity entity = new MarketProviderMappingEntity();
        entity.setId(UUID.randomUUID());
        entity.setSource(source);
        entity.setExternalSymbol(externalSymbol);
        entity.setEnabled(true);
        entity.setPriority(1);
        entity.setRefreshIntervalMinutes(5);
        entity.setHistoryStartDate(LocalDate.of(2020, 1, 1));
        entity.setLastRefreshStatus(MappingRefreshStatus.PENDING);
        entity.setInstrument(instrument);
        return entity;
    }

    private MarketInstrumentEntity instrument(String symbol,
                                              String displayName,
                                              InstrumentType type,
                                              boolean enabled) {
        MarketInstrumentEntity entity = new MarketInstrumentEntity();
        entity.setId(UUID.randomUUID());
        entity.setSymbol(symbol);
        entity.setDisplayName(displayName);
        entity.setType(type);
        entity.setEnabled(enabled);
        return entity;
    }
}
