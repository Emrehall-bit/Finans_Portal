package com.emrehalli.financeportal.market.provider;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ProviderFetchRequest(
        DataSource source,
        List<String> symbols,
        Set<InstrumentType> instrumentTypes,
        LocalDate from,
        LocalDate to,
        Map<String, String> filters
) {
    public ProviderFetchRequest {
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        instrumentTypes = instrumentTypes == null ? Set.of() : Set.copyOf(instrumentTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public static ProviderFetchRequest all() {
        return new ProviderFetchRequest(null, List.of(), Set.of(), null, null, Map.of());
    }

    public static ProviderFetchRequest forSource(DataSource source) {
        return new ProviderFetchRequest(source, List.of(), Set.of(), null, null, Map.of());
    }

    public static ProviderFetchRequest forSymbols(List<String> symbols) {
        return new ProviderFetchRequest(null, symbols, Set.of(), null, null, Map.of());
    }

    public boolean hasSymbolFilter() {
        return symbols != null && !symbols.isEmpty();
    }

    public boolean hasInstrumentTypeFilter() {
        return instrumentTypes != null && !instrumentTypes.isEmpty();
    }

    public boolean hasSourceFilter() {
        return source != null;
    }
}
