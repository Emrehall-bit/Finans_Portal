package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataSeederTest {

    @Mock
    private MarketInstrumentRepository marketInstrumentRepository;

    @Mock
    private MarketProviderMappingRepository marketProviderMappingRepository;

    @Test
    void seedsOnlyVerifiedEvdsMacroSeries() throws Exception {
        when(marketInstrumentRepository.count()).thenReturn(0L);
        when(marketProviderMappingRepository.count()).thenReturn(0L);
        when(marketInstrumentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketProviderMappingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        MarketRefreshProperties properties = new MarketRefreshProperties();
        MarketRefreshProperties.ProviderPolicy evdsPolicy = new MarketRefreshProperties.ProviderPolicy();
        evdsPolicy.setEnabled(true);
        evdsPolicy.setRefreshMinutes(15);
        properties.setProviders(java.util.Map.of("evds", evdsPolicy));

        MarketDataSeeder seeder = new MarketDataSeeder(
                marketInstrumentRepository,
                marketProviderMappingRepository,
                properties,
                new SymbolNormalizer(),
                Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC)
        );

        seeder.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketInstrumentEntity>> instrumentCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketProviderMappingEntity>> mappingCaptor = ArgumentCaptor.forClass(List.class);

        org.mockito.Mockito.verify(marketInstrumentRepository).saveAll(instrumentCaptor.capture());
        org.mockito.Mockito.verify(marketProviderMappingRepository).saveAll(mappingCaptor.capture());

        assertThat(instrumentCaptor.getValue())
                .extracting(MarketInstrumentEntity::getSymbol)
                .contains(
                        "TCMBTUFEAYLIK",
                        "TCMBTUFEYILLIK",
                        "TCMBUFEAYLIK",
                        "TCMBUFEYILLIK",
                        "TCMBISSIZLIK",
                        "TCMBFAIZ",
                        "TCMBMEVFAIZ",
                        "BISTBILESIK",
                        "AFT",
                        "AFA",
                        "MAC",
                        "IPB",
                        "IIH",
                        "NNF",
                        "YAS",
                        "DVT",
                        "GMR",
                        "KPH",
                        "CPU",
                        "SAS",
                        "GSP",
                        "TCD",
                        "YKT"
                )
                .doesNotContain("XAUTRY", "XAGTRY", "XPTTRY", "TCMBUSDKURU", "TCMBEURKURU", "TCMBCARIACIK", "TCMBBIST100");

        List<String> evdsMappings = mappingCaptor.getValue().stream()
                .filter(mapping -> mapping.getSource() == DataSource.EVDS)
                .map(mapping -> mapping.getInstrument().getSymbol() + "=" + mapping.getExternalSymbol())
                .toList();

        assertThat(evdsMappings)
                .contains(
                        "TCMBTUFEAYLIK=TP.TUKFIY2025.GENEL-1",
                        "TCMBTUFEYILLIK=TP.TUKFIY2025.GENEL-3",
                        "TCMBUFEAYLIK=TP.TUFE1YI.T1-1",
                        "TCMBUFEYILLIK=TP.TUFE1YI.T1-3",
                        "TCMBISSIZLIK=TP.TIG08",
                        "TCMBFAIZ=TP.BISPOLFAIZ.TUR",
                        "TCMBMEVFAIZ=TP.TRY.MT06",
                        "BISTBILESIK=TP.MK.F.BILESIK"
                )
                .noneMatch(entry -> entry.startsWith("XAUTRY=")
                        || entry.startsWith("XAGTRY=")
                        || entry.startsWith("XPTTRY=")
                        || entry.startsWith("TCMBUSDKURU=")
                        || entry.startsWith("TCMBEURKURU=")
                        || entry.startsWith("TCMBBIST100=")
                        || entry.startsWith("TCMBCARIACIK=")
                        || entry.equals("TCMBTUFE=TP.TUKFIY2025.GENEL-1")
                        || entry.equals("TCMBUFE=TP.TUKFIY2025.GENEL-3"));

        List<String> tefasMappings = mappingCaptor.getValue().stream()
                .filter(mapping -> mapping.getSource() == DataSource.TEFAS)
                .map(mapping -> mapping.getInstrument().getSymbol() + "=" + mapping.getExternalSymbol())
                .toList();

        assertThat(tefasMappings)
                .contains(
                        "AFT=AFT",
                        "AFA=AFA",
                        "MAC=MAC",
                        "IPB=IPB",
                        "IIH=IIH",
                        "NNF=NNF",
                        "YAS=YAS",
                        "DVT=DVT",
                        "GMR=GMR",
                        "KPH=KPH",
                        "CPU=CPU",
                        "SAS=SAS",
                        "GSP=GSP",
                        "TCD=TCD",
                        "YKT=YKT"
                );
    }

    @Test
    void syncsExistingMappingRefreshIntervalsFromConfigWhenRegistryAlreadyExists() throws Exception {
        when(marketInstrumentRepository.count()).thenReturn(1L);
        when(marketProviderMappingRepository.count()).thenReturn(1L);

        MarketProviderMappingEntity bistMapping = new MarketProviderMappingEntity();
        bistMapping.setId(UUID.randomUUID());
        bistMapping.setSource(DataSource.BIST);
        bistMapping.setRefreshIntervalMinutes(30);
        bistMapping.setEnabled(true);

        when(marketProviderMappingRepository.findAll()).thenReturn(List.of(bistMapping));
        when(marketProviderMappingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        MarketRefreshProperties properties = new MarketRefreshProperties();
        MarketRefreshProperties.ProviderPolicy bistPolicy = new MarketRefreshProperties.ProviderPolicy();
        bistPolicy.setEnabled(true);
        bistPolicy.setRefreshMinutes(15);
        properties.setProviders(java.util.Map.of("bist", bistPolicy));

        MarketDataSeeder seeder = new MarketDataSeeder(
                marketInstrumentRepository,
                marketProviderMappingRepository,
                properties,
                new SymbolNormalizer(),
                Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC)
        );

        seeder.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));

        org.mockito.Mockito.verify(marketProviderMappingRepository).saveAll(argThat(mappings -> {
            List<MarketProviderMappingEntity> capturedMappings = new ArrayList<>();
            mappings.forEach(capturedMappings::add);
            return capturedMappings.size() == 1
                    && capturedMappings.getFirst().getRefreshIntervalMinutes() == 15;
        }));
        org.mockito.Mockito.verify(marketInstrumentRepository, never()).saveAll(anyList());
    }
}
