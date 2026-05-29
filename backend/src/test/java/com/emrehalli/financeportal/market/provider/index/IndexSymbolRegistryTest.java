package com.emrehalli.financeportal.market.provider.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexSymbolRegistryTest {

    private IndexSymbolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new IndexSymbolRegistry();
    }

    @Test
    void yahoo_symbols_are_returned() {
        assertThat(registry.getAllYahooSymbols())
                .containsExactlyInAnyOrder(
                        "XU100.IS", "XU030.IS", "XU050.IS",
                        "XBANK.IS", "XUSIN.IS", "XUTEK.IS", "XHOLD.IS");
    }

    @Test
    void instrument_codes_are_returned() {
        assertThat(registry.getAllCodes())
                .containsExactlyInAnyOrder(
                        "BIST100", "BIST30", "BIST50",
                        "XBANK", "XUSIN", "XUTEK", "XHOLD");
    }

    @Test
    void yahoo_symbol_maps_to_instrument_code() {
        assertThat(registry.toInstrumentCode("XU100.IS")).isEqualTo("BIST100");
        assertThat(registry.toInstrumentCode("XU030.IS")).isEqualTo("BIST30");
        assertThat(registry.toInstrumentCode("XU050.IS")).isEqualTo("BIST50");
        assertThat(registry.toInstrumentCode("XBANK.IS")).isEqualTo("XBANK");
        assertThat(registry.toInstrumentCode("XUSIN.IS")).isEqualTo("XUSIN");
        assertThat(registry.toInstrumentCode("XUTEK.IS")).isEqualTo("XUTEK");
        assertThat(registry.toInstrumentCode("XHOLD.IS")).isEqualTo("XHOLD");
    }

    @Test
    void instrument_code_maps_to_yahoo_symbol() {
        assertThat(registry.toYahooSymbol("BIST100")).isEqualTo("XU100.IS");
        assertThat(registry.toYahooSymbol("BIST50")).isEqualTo("XU050.IS");
        assertThat(registry.toYahooSymbol("XBANK")).isEqualTo("XBANK.IS");
        assertThat(registry.toYahooSymbol("SP500")).isNull();
    }

    @Test
    void unknown_yahoo_symbol_returns_null() {
        assertThat(registry.toInstrumentCode("UNKNOWN")).isNull();
    }

    @Test
    void display_names_are_populated() {
        assertThat(registry.toDisplayName("BIST100")).isEqualTo("BIST 100");
        assertThat(registry.toDisplayName("XBANK")).isEqualTo("BIST Banka");
        assertThat(registry.toDisplayName("XUTEK")).isEqualTo("BIST Teknoloji");
        assertThat(registry.toDisplayName("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}




