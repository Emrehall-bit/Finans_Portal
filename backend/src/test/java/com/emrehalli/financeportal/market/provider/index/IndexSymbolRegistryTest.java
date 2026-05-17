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
                .containsExactlyInAnyOrder("XU100.IS", "XU030.IS", "^GSPC", "^IXIC", "^DJI", "^GDAXI");
    }

    @Test
    void instrument_codes_are_returned() {
        assertThat(registry.getAllCodes())
                .containsExactlyInAnyOrder("BIST100", "BIST30", "SP500", "NASDAQ", "DOWJONES", "DAX");
    }

    @Test
    void yahoo_symbol_maps_to_instrument_code() {
        assertThat(registry.toInstrumentCode("XU100.IS")).isEqualTo("BIST100");
        assertThat(registry.toInstrumentCode("XU030.IS")).isEqualTo("BIST30");
        assertThat(registry.toInstrumentCode("^GSPC")).isEqualTo("SP500");
        assertThat(registry.toInstrumentCode("^IXIC")).isEqualTo("NASDAQ");
        assertThat(registry.toInstrumentCode("^DJI")).isEqualTo("DOWJONES");
        assertThat(registry.toInstrumentCode("^GDAXI")).isEqualTo("DAX");
    }

    @Test
    void instrument_code_maps_to_yahoo_symbol() {
        assertThat(registry.toYahooSymbol("BIST100")).isEqualTo("XU100.IS");
        assertThat(registry.toYahooSymbol("SP500")).isEqualTo("^GSPC");
        assertThat(registry.toYahooSymbol("GOLD")).isNull();
    }

    @Test
    void unknown_yahoo_symbol_returns_null() {
        assertThat(registry.toInstrumentCode("UNKNOWN")).isNull();
    }

    @Test
    void display_names_are_populated() {
        assertThat(registry.toDisplayName("BIST100")).isEqualTo("BIST 100");
        assertThat(registry.toDisplayName("SP500")).isEqualTo("S&P 500");
        assertThat(registry.toDisplayName("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
