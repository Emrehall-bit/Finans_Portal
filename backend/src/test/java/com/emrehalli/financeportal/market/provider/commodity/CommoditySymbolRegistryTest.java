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
        assertThat(registry.getAllYahooSymbols())
                .containsExactlyInAnyOrder("GC=F", "SI=F", "BZ=F", "CL=F", "NG=F");
    }

    @Test
    void instrument_codes_are_returned() {
        assertThat(registry.getAllCodes())
                .containsExactlyInAnyOrder("GOLD", "SILVER", "BRENT", "WTI", "NATGAS");
    }

    @Test
    void yahoo_symbol_maps_to_instrument_code() {
        assertThat(registry.toInstrumentCode("GC=F")).isEqualTo("GOLD");
        assertThat(registry.toInstrumentCode("SI=F")).isEqualTo("SILVER");
        assertThat(registry.toInstrumentCode("BZ=F")).isEqualTo("BRENT");
        assertThat(registry.toInstrumentCode("CL=F")).isEqualTo("WTI");
        assertThat(registry.toInstrumentCode("NG=F")).isEqualTo("NATGAS");
    }

    @Test
    void instrument_code_maps_to_yahoo_symbol() {
        assertThat(registry.toYahooSymbol("GOLD")).isEqualTo("GC=F");
        assertThat(registry.toYahooSymbol("WTI")).isEqualTo("CL=F");
        assertThat(registry.toYahooSymbol("SP500")).isNull();
    }

    @Test
    void unknown_yahoo_symbol_returns_null() {
        assertThat(registry.toInstrumentCode("UNKNOWN")).isNull();
    }

    @Test
    void display_names_are_populated() {
        assertThat(registry.toDisplayName("GOLD")).isEqualTo("Altın (Vadeli)");
        assertThat(registry.toDisplayName("BRENT")).isEqualTo("Brent Petrol");
        assertThat(registry.toDisplayName("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
