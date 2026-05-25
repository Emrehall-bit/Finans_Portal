package com.emrehalli.financeportal.market.provider.commodity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommoditySymbolRegistryTest {

    private CommoditySymbolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommoditySymbolRegistry();
    }

    @Test
    void yahoo_symbols_are_returned() {
        // Only 3 symbols are fetched from Yahoo; calculated instruments have no Yahoo symbol
        assertThat(registry.getAllYahooSymbols())
                .containsExactlyInAnyOrder("GC=F", "SI=F", "BZ=F");
    }

    @Test
    void instrument_codes_are_returned() {
        assertThat(registry.getAllCodes())
                .containsExactlyInAnyOrder("BRENT", "GOLD_USD", "SILVER_USD");
    }

    @Test
    void yahoo_symbol_maps_to_instrument_code() {
        assertThat(registry.toInstrumentCode("GC=F")).isEqualTo("GOLD_USD");
        assertThat(registry.toInstrumentCode("SI=F")).isEqualTo("SILVER_USD");
        assertThat(registry.toInstrumentCode("BZ=F")).isEqualTo("BRENT");
    }

    @Test
    void instrument_code_maps_to_yahoo_symbol() {
        assertThat(registry.toYahooSymbol("BRENT")).isEqualTo("BZ=F");
        assertThat(registry.toYahooSymbol("GOLD_USD")).isEqualTo("GC=F");
        // Calculated instruments have no Yahoo symbol
        assertThat(registry.toYahooSymbol("GRAM_ALTIN")).isNull();
    }

    @Test
    void unknown_yahoo_symbol_returns_null() {
        assertThat(registry.toInstrumentCode("UNKNOWN")).isNull();
    }

    @Test
    void display_names_are_populated() {
        assertThat(registry.toDisplayName("BRENT")).isEqualTo("Brent Petrol");
        assertThat(registry.toDisplayName("GOLD_USD")).isEqualTo("Altın (Ham USD)");
        assertThat(registry.toDisplayName("GRAM_ALTIN")).isEqualTo("Gram Altın");
        assertThat(registry.toDisplayName("CEYREK_ALTIN")).isEqualTo("Çeyrek Altın");
        assertThat(registry.toDisplayName("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}



