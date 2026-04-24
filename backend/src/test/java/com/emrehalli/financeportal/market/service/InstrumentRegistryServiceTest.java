package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentRegistryServiceTest {

    private final InstrumentRegistryService instrumentRegistryService =
            new InstrumentRegistryService(new SymbolNormalizer());

    @Test
    void getBySymbolResolvesCanonicalSymbolFromUserFacingVariations() {
        assertThat(instrumentRegistryService.getBySymbol("usdtry")).isPresent();
        assertThat(instrumentRegistryService.getBySymbol("USD/TRY")).isPresent();
        assertThat(instrumentRegistryService.getBySymbol(" usd / try ")).isPresent();
    }

    @Test
    void providerCodeMappingStaysSeparateFromCanonicalSymbolNormalization() {
        var definition = instrumentRegistryService.getBySymbol("usd/try");

        assertThat(definition).isPresent();
        assertThat(definition.get().symbol()).isEqualTo("USDTRY");
        assertThat(definition.get().providerCode(DataSource.EVDS)).contains("TP.DK.USD.S.YTL");
    }

    @Test
    void getByProviderCodeReturnsMappedInstrumentInConstantTimeIndexLookup() {
        var definition = instrumentRegistryService.getByProviderCode(DataSource.EVDS, " tp.dk.usd.s.ytl ");

        assertThat(definition).isPresent();
        assertThat(definition.get().symbol()).isEqualTo("USDTRY");
    }

    @Test
    void duplicateProviderCodeMappingFailsFastAtConstructionTime() {
        SymbolNormalizer symbolNormalizer = new SymbolNormalizer();

        assertThatThrownBy(() -> new InstrumentRegistryService(symbolNormalizer, List.of(
                new InstrumentRegistryService.InstrumentDefinition(
                        "USDTRY",
                        "USD/TRY",
                        InstrumentType.FX,
                        "TRY",
                        Map.of(DataSource.EVDS, "TP.DK.USD.S.YTL")
                ),
                new InstrumentRegistryService.InstrumentDefinition(
                        "ALTUSDTRY",
                        "Alt USD/TRY",
                        InstrumentType.FX,
                        "TRY",
                        Map.of(DataSource.EVDS, "TP.DK.USD.S.YTL")
                )
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate provider code mapping detected");
    }
}
