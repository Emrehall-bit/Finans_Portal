package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class InstrumentRegistryService {

    private final SymbolNormalizer symbolNormalizer;
    private final Map<String, InstrumentDefinition> definitions;
    private final Map<ProviderCodeKey, InstrumentDefinition> providerCodeIndex;

    @Autowired
    public InstrumentRegistryService(SymbolNormalizer symbolNormalizer) {
        this(symbolNormalizer, List.of(
                new InstrumentDefinition(
                        "USDTRY",
                        "USD/TRY",
                        InstrumentType.FX,
                        "TRY",
                        Map.of(DataSource.EVDS, "TP.DK.USD.S.YTL")
                ),
                new InstrumentDefinition(
                        "EURTRY",
                        "EUR/TRY",
                        InstrumentType.FX,
                        "TRY",
                        Map.of(DataSource.EVDS, "TP.DK.EUR.S.YTL")
                ),
                new InstrumentDefinition(
                        "GBPTRY",
                        "GBP/TRY",
                        InstrumentType.FX,
                        "TRY",
                        Map.of(DataSource.EVDS, "TP.DK.GBP.S.YTL")
                )
        ));
    }

    InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                              List<InstrumentDefinition> instrumentDefinitions) {
        this.symbolNormalizer = symbolNormalizer;
        this.definitions = buildCanonicalSymbolIndex(instrumentDefinitions);
        this.providerCodeIndex = buildProviderCodeIndex(instrumentDefinitions);
    }

    public List<InstrumentDefinition> getAll() {
        return definitions.values().stream().toList();
    }

    public List<InstrumentDefinition> getBySource(DataSource source) {
        return definitions.values().stream()
                .filter(def -> def.supports(source))
                .toList();
    }

    public Optional<InstrumentDefinition> getBySymbol(String symbol) {
        return symbolNormalizer.normalize(symbol)
                .map(definitions::get);
    }

    public Optional<InstrumentDefinition> getByProviderCode(DataSource source, String providerCode) {
        Optional<ProviderCodeKey> key = toProviderCodeKey(source, providerCode);
        if (key.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(providerCodeIndex.get(key.get()));
    }

    private Map<String, InstrumentDefinition> buildCanonicalSymbolIndex(List<InstrumentDefinition> instrumentDefinitions) {
        Map<String, InstrumentDefinition> index = new LinkedHashMap<>();

        for (InstrumentDefinition definition : instrumentDefinitions) {
            String canonicalSymbol = symbolNormalizer.normalize(definition.symbol())
                    .orElseThrow(() -> new IllegalStateException("Instrument symbol cannot be blank"));
            index.put(canonicalSymbol, definition);
        }

        return Map.copyOf(index);
    }

    private Map<ProviderCodeKey, InstrumentDefinition> buildProviderCodeIndex(List<InstrumentDefinition> instrumentDefinitions) {
        Map<ProviderCodeKey, InstrumentDefinition> index = new LinkedHashMap<>();

        for (InstrumentDefinition definition : instrumentDefinitions) {
            for (Map.Entry<DataSource, String> entry : definition.providerCodes().entrySet()) {
                ProviderCodeKey key = toProviderCodeKey(entry.getKey(), entry.getValue())
                        .orElseThrow(() -> new IllegalStateException(
                                "Provider code cannot be blank for symbol: " + definition.symbol()
                        ));

                InstrumentDefinition previous = index.putIfAbsent(key, definition);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate provider code mapping detected for source " + entry.getKey()
                                    + ": " + entry.getValue()
                                    + " is mapped to both " + previous.symbol()
                                    + " and " + definition.symbol()
                    );
                }
            }
        }

        return Map.copyOf(index);
    }

    private Optional<ProviderCodeKey> toProviderCodeKey(DataSource source, String providerCode) {
        if (source == null || providerCode == null || providerCode.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ProviderCodeKey(
                source,
                providerCode.trim().toUpperCase()
        ));
    }

    public record InstrumentDefinition(
            String symbol,
            String displayName,
            InstrumentType instrumentType,
            String currency,
            Map<DataSource, String> providerCodes
    ) {
        public boolean supports(DataSource source) {
            return providerCodes.containsKey(source);
        }

        public Set<DataSource> supportedProviders() {
            return providerCodes.keySet();
        }

        public Optional<String> providerCode(DataSource source) {
            return Optional.ofNullable(providerCodes.get(source));
        }
    }

    private record ProviderCodeKey(
            DataSource source,
            String providerCode
    ) {
    }
}
