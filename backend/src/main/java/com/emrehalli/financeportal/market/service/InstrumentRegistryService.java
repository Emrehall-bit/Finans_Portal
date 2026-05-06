package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.provider.binance.config.BinanceProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InstrumentRegistryService {

    private final SymbolNormalizer symbolNormalizer;
    private final MarketProviderMappingRepository marketProviderMappingRepository;
    private final BinanceProviderProperties binanceProviderProperties;
    private final BistProviderProperties bistProviderProperties;
    private final EvdsProperties evdsProperties;

    private List<InstrumentDefinition> seededDefinitionList = List.of();
    private Map<String, InstrumentDefinition> seededDefinitions = Map.of();
    private Map<ProviderCodeKey, InstrumentDefinition> seededProviderCodeIndex = Map.of();

    @Autowired
    public InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                                     MarketProviderMappingRepository marketProviderMappingRepository,
                                     BinanceProviderProperties binanceProviderProperties,
                                     BistProviderProperties bistProviderProperties,
                                     EvdsProperties evdsProperties) {
        this.symbolNormalizer = symbolNormalizer;
        this.marketProviderMappingRepository = marketProviderMappingRepository;
        this.binanceProviderProperties = binanceProviderProperties;
        this.bistProviderProperties = bistProviderProperties;
        this.evdsProperties = evdsProperties;
    }

    InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                              MarketProviderMappingRepository marketProviderMappingRepository,
                              BinanceProviderProperties binanceProviderProperties,
                              BistProviderProperties bistProviderProperties,
                              EvdsProperties evdsProperties,
                              boolean testConstructorMarker) {
        this(symbolNormalizer, marketProviderMappingRepository, binanceProviderProperties, bistProviderProperties, evdsProperties);
    }

    InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                              List<InstrumentDefinition> instrumentDefinitions) {
        this.symbolNormalizer = symbolNormalizer;
        this.marketProviderMappingRepository = null;
        this.binanceProviderProperties = new BinanceProviderProperties();
        this.bistProviderProperties = new BistProviderProperties();
        this.evdsProperties = new EvdsProperties();
        seedFallbackDefinitions(instrumentDefinitions);
    }

    public static InstrumentRegistryService seeded(SymbolNormalizer symbolNormalizer,
                                                   List<InstrumentDefinition> instrumentDefinitions) {
        return new InstrumentRegistryService(symbolNormalizer, instrumentDefinitions);
    }

    public Resolution resolveMappings(DataSource source) {
        if (marketProviderMappingRepository == null && !seededDefinitions.isEmpty()) {
            return new Resolution(source, seededMappings(source), false);
        }

        List<ResolvedMapping> dbMappings = source == null ? List.of() : loadDbMappings(source);
        if (!dbMappings.isEmpty()) {
            return new Resolution(source, dbMappings, false);
        }

        return new Resolution(source, fallbackMappings(source), true);
    }

    public List<InstrumentDefinition> getAll() {
        return buildIndexes(loadEffectiveDefinitions()).definitions().values().stream().toList();
    }

    public List<InstrumentDefinition> getBySource(DataSource source) {
        return buildIndexes(definitionsForSource(source)).definitions().values().stream().toList();
    }

    public Optional<InstrumentDefinition> getBySymbol(String symbol) {
        return symbolNormalizer.normalize(symbol)
                .map(value -> buildIndexes(loadEffectiveDefinitions()).definitions().get(value));
    }

    public Optional<InstrumentDefinition> getByProviderCode(DataSource source, String providerCode) {
        Optional<ProviderCodeKey> key = toProviderCodeKey(source, providerCode);
        if (key.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(buildIndexes(loadEffectiveDefinitions()).providerCodeIndex().get(key.get()));
    }

    private void seedFallbackDefinitions(List<InstrumentDefinition> instrumentDefinitions) {
        seededDefinitionList = List.copyOf(instrumentDefinitions);
        seededDefinitions = buildCanonicalSymbolIndex(instrumentDefinitions);
        seededProviderCodeIndex = buildProviderCodeIndex(instrumentDefinitions);
    }

    private List<ResolvedMapping> seededMappings(DataSource source) {
        if (source == null) {
            return List.of();
        }

        List<ResolvedMapping> mappings = new ArrayList<>();
        int priority = 0;
        for (InstrumentDefinition definition : seededDefinitionList) {
            String providerSymbol = definition.providerCodes().get(source);
            if (providerSymbol == null || providerSymbol.isBlank()) {
                continue;
            }
            mappings.add(new ResolvedMapping(
                    source,
                    definition.symbol(),
                    definition.displayName(),
                    definition.instrumentType(),
                    definition.currency(),
                    providerSymbol,
                    priority++,
                    null
            ));
        }
        return List.copyOf(mappings);
    }

    private Indexes buildIndexes(List<InstrumentDefinition> instrumentDefinitions) {
        if (marketProviderMappingRepository == null && !seededDefinitions.isEmpty()) {
            return new Indexes(seededDefinitions, seededProviderCodeIndex);
        }

        return new Indexes(
                buildCanonicalSymbolIndex(instrumentDefinitions),
                buildProviderCodeIndex(instrumentDefinitions)
        );
    }

    private List<InstrumentDefinition> loadEffectiveDefinitions() {
        if (marketProviderMappingRepository == null && !seededDefinitions.isEmpty()) {
            return seededDefinitions.values().stream().toList();
        }

        Map<String, InstrumentDefinitionBuilder> builders = new LinkedHashMap<>();

        for (DataSource source : List.of(DataSource.BINANCE, DataSource.BIST, DataSource.EVDS)) {
            for (ResolvedMapping mapping : resolveMappings(source).mappings()) {
                String canonicalSymbol = symbolNormalizer.normalize(mapping.symbol())
                        .orElseThrow(() -> new IllegalStateException("Instrument symbol cannot be blank"));

                builders.computeIfAbsent(canonicalSymbol, ignored -> new InstrumentDefinitionBuilder(
                        mapping.symbol(),
                        mapping.displayName(),
                        mapping.instrumentType(),
                        mapping.currency()
                )).providerCodes.put(source, mapping.providerSymbol());
            }
        }

        return builders.values().stream()
                .map(InstrumentDefinitionBuilder::build)
                .toList();
    }

    private List<InstrumentDefinition> definitionsForSource(DataSource source) {
        return resolveMappings(source).mappings().stream()
                .map(mapping -> new InstrumentDefinition(
                        mapping.symbol(),
                        mapping.displayName(),
                        mapping.instrumentType(),
                        mapping.currency(),
                        Map.of(mapping.source(), mapping.providerSymbol())
                ))
                .toList();
    }

    private List<ResolvedMapping> loadDbMappings(DataSource source) {
        if (marketProviderMappingRepository == null || source == null) {
            return List.of();
        }

        return marketProviderMappingRepository
                .findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(source)
                .stream()
                .filter(MarketProviderMappingEntity::isEnabled)
                .filter(entity -> entity.getInstrument() != null && entity.getInstrument().isActive())
                .map(this::toResolvedMapping)
                .toList();
    }

    private ResolvedMapping toResolvedMapping(MarketProviderMappingEntity entity) {
        return new ResolvedMapping(
                entity.getProviderSource(),
                entity.getInstrument().getSymbol(),
                entity.getInstrument().getName(),
                entity.getInstrument().getInstrumentType(),
                entity.getInstrument().getCurrency(),
                entity.getProviderSymbol(),
                entity.getPriority(),
                entity.getRefreshIntervalSeconds()
        );
    }

    private List<ResolvedMapping> fallbackMappings(DataSource source) {
        if (source == null) {
            return List.of();
        }

        return switch (source) {
            case BINANCE -> fallbackBinanceMappings();
            case BIST -> fallbackBistMappings();
            case EVDS -> fallbackEvdsMappings();
            default -> List.of();
        };
    }

    private List<ResolvedMapping> fallbackBinanceMappings() {
        List<ResolvedMapping> mappings = new ArrayList<>();
        int index = 0;
        for (String symbol : distinctNormalized(binanceProviderProperties.getSymbols())) {
            mappings.add(new ResolvedMapping(
                    DataSource.BINANCE,
                    symbol,
                    symbol,
                    InstrumentType.CRYPTO,
                    resolveBinanceCurrency(symbol),
                    symbol,
                    index++,
                    null
            ));
        }
        return List.copyOf(mappings);
    }

    private List<ResolvedMapping> fallbackBistMappings() {
        List<ResolvedMapping> mappings = new ArrayList<>();
        int index = 0;
        for (String providerSymbol : distinctUppercase(bistProviderProperties.getSymbols())) {
            String symbol = providerSymbol.endsWith(".IS")
                    ? providerSymbol.substring(0, providerSymbol.length() - 3)
                    : providerSymbol;
            mappings.add(new ResolvedMapping(
                    DataSource.BIST,
                    symbol,
                    symbol,
                    InstrumentType.STOCK,
                    "TRY",
                    providerSymbol,
                    index++,
                    null
            ));
        }
        return List.copyOf(mappings);
    }

    private List<ResolvedMapping> fallbackEvdsMappings() {
        List<ResolvedMapping> mappings = new ArrayList<>();
        int index = 0;
        for (EvdsProperties.SeriesConfig series : evdsProperties.getSeries()) {
            if (series.getApiCode() == null || series.getApiCode().isBlank()) {
                continue;
            }
            mappings.add(new ResolvedMapping(
                    DataSource.EVDS,
                    series.getSymbol(),
                    series.getName(),
                    series.getInstrumentType(),
                    series.getCurrency(),
                    series.getApiCode(),
                    index++,
                    null
            ));
        }
        return List.copyOf(mappings);
    }

    private List<String> distinctNormalized(List<String> values) {
        return (values == null ? List.<String>of() : values).stream()
                .flatMap(value -> symbolNormalizer.normalize(value).stream())
                .distinct()
                .toList();
    }

    private List<String> distinctUppercase(List<String> values) {
        Set<String> symbols = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            symbols.add(value.trim().toUpperCase());
        }
        return List.copyOf(symbols);
    }

    private String resolveBinanceCurrency(String symbol) {
        if (symbol != null && symbol.endsWith("USDT")) {
            return "USDT";
        }

        return null;
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

    private record Indexes(
            Map<String, InstrumentDefinition> definitions,
            Map<ProviderCodeKey, InstrumentDefinition> providerCodeIndex
    ) {
    }

    private static final class InstrumentDefinitionBuilder {
        private final String symbol;
        private final String displayName;
        private final InstrumentType instrumentType;
        private final String currency;
        private final Map<DataSource, String> providerCodes = new EnumMap<>(DataSource.class);

        private InstrumentDefinitionBuilder(String symbol, String displayName, InstrumentType instrumentType, String currency) {
            this.symbol = symbol;
            this.displayName = displayName;
            this.instrumentType = instrumentType;
            this.currency = currency;
        }

        private InstrumentDefinition build() {
            return new InstrumentDefinition(symbol, displayName, instrumentType, currency, Map.copyOf(providerCodes));
        }
    }

    public record ResolvedMapping(
            DataSource source,
            String symbol,
            String displayName,
            InstrumentType instrumentType,
            String currency,
            String providerSymbol,
            int priority,
            Integer refreshIntervalSeconds
    ) {
    }

    public record Resolution(
            DataSource source,
            List<ResolvedMapping> mappings,
            boolean fallbackToYaml
    ) {
        public Resolution {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
        }
    }
}
