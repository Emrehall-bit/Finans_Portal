package com.emrehalli.financeportal.market.provider;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFetchRequestTest {

    @Test
    void constructorNormalizesNullCollectionsToEmptyCollections() {
        ProviderFetchRequest request = new ProviderFetchRequest(
                DataSource.EVDS,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(request.source()).isEqualTo(DataSource.EVDS);
        assertThat(request.symbols()).isEmpty();
        assertThat(request.instrumentTypes()).isEmpty();
        assertThat(request.filters()).isEmpty();
        assertThat(request.hasSourceFilter()).isTrue();
        assertThat(request.hasSymbolFilter()).isFalse();
        assertThat(request.hasInstrumentTypeFilter()).isFalse();
    }

    @Test
    void detectsSourceSymbolAndInstrumentTypeFilters() {
        ProviderFetchRequest request = new ProviderFetchRequest(
                DataSource.EVDS,
                List.of("USDTRY"),
                Set.of(InstrumentType.FX),
                null,
                null,
                Map.of("frequency", "daily")
        );

        assertThat(request.hasSourceFilter()).isTrue();
        assertThat(request.hasSymbolFilter()).isTrue();
        assertThat(request.hasInstrumentTypeFilter()).isTrue();
    }
}
