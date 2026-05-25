package com.emrehalli.financeportal.market.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InstrumentTypeTest {

    @Test
    void index_and_commodity_are_defined() {
        assertThat(InstrumentType.INDEX).isNotNull();
        assertThat(InstrumentType.COMMODITY).isNotNull();
    }

    @Test
    void valueOf_index_and_commodity_by_name() {
        assertThat(InstrumentType.valueOf("INDEX")).isEqualTo(InstrumentType.INDEX);
        assertThat(InstrumentType.valueOf("COMMODITY")).isEqualTo(InstrumentType.COMMODITY);
    }

    @Test
    void existing_types_still_present() {
        assertThatCode(() -> {
            InstrumentType.valueOf("FX");
            InstrumentType.valueOf("CRYPTO");
            InstrumentType.valueOf("FUND");
            InstrumentType.valueOf("STOCK");
            InstrumentType.valueOf("FUTURES");
            InstrumentType.valueOf("BOND");
        }).doesNotThrowAnyException();
    }
}



