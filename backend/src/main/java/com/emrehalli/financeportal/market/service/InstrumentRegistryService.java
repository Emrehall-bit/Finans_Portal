package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central registry service that manages canonical instruments and provider mappings from the database.
 */
@Service
@Transactional(readOnly = true)
public class InstrumentRegistryService {

    private final SymbolNormalizer symbolNormalizer;
    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketProviderMappingRepository marketProviderMappingRepository;
    private final Clock clock;
    private final List<InstrumentDefinition> seededDefinitions;

    /**
     * Creates the production registry service.
     *
     * @param symbolNormalizer symbol normalization helper
     * @param marketInstrumentRepository instrument repository
     * @param marketProviderMappingRepository mapping repository
     * @param clock wall clock used for refresh timestamps
     */
    @Autowired
    public InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                                     MarketInstrumentRepository marketInstrumentRepository,
                                     MarketProviderMappingRepository marketProviderMappingRepository,
                                     Clock clock) {
        this(symbolNormalizer, marketInstrumentRepository, marketProviderMappingRepository, clock, List.of());
    }

    private InstrumentRegistryService(SymbolNormalizer symbolNormalizer,
                                      MarketInstrumentRepository marketInstrumentRepository,
                                      MarketProviderMappingRepository marketProviderMappingRepository,
                                      Clock clock,
                                      List<InstrumentDefinition> seededDefinitions) {
        this.symbolNormalizer = symbolNormalizer;
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketProviderMappingRepository = marketProviderMappingRepository;
        this.clock = clock;
        this.seededDefinitions = List.copyOf(seededDefinitions);
    }

    /**
     * Creates a seeded registry instance for isolated tests.
     *
     * @param symbolNormalizer symbol normalization helper
     * @param instrumentDefinitions seeded definitions
     * @return seeded test registry
     */
    public static InstrumentRegistryService seeded(SymbolNormalizer symbolNormalizer,
                                                   List<InstrumentDefinition> instrumentDefinitions) {
        return new InstrumentRegistryService(symbolNormalizer, null, null, Clock.systemUTC(), instrumentDefinitions);
    }

    /**
     * Resolves active mappings for a provider ordered by priority.
     *
     * @param source provider source
     * @return active resolution for the source
     */
    public Resolution resolveMappings(DataSource source) {
        if (marketProviderMappingRepository == null) {
            return new Resolution(source, seededMappings(source));
        }

        return new Resolution(source, marketProviderMappingRepository
                .findBySourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(source)
                .stream()
                .map(this::toResolvedMapping)
                .toList());
    }

    /**
     * Lists all canonical instruments with optional filters.
     *
     * @param type instrument type filter
     * @param enabled enabled flag filter
     * @return matching instruments
     */
    public List<MarketInstrumentEntity> getInstruments(InstrumentType type, Boolean enabled) {
        if (marketInstrumentRepository == null) {
            return List.of();
        }
        if (type != null && enabled != null) {
            return marketInstrumentRepository.findByTypeAndEnabled(type, enabled);
        }
        if (type != null) {
            return marketInstrumentRepository.findByType(type);
        }
        if (enabled != null) {
            return marketInstrumentRepository.findByEnabled(enabled);
        }
        return marketInstrumentRepository.findAll();
    }

    /**
     * Finds an instrument by identifier.
     *
     * @param instrumentId instrument identifier
     * @return matching instrument
     */
    public MarketInstrumentEntity getInstrument(UUID instrumentId) {
        return marketInstrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));
    }

    /**
     * Creates a new canonical instrument.
     *
     * @param symbol input symbol
     * @param displayName display name
     * @param type instrument type
     * @param enabled enabled flag
     * @return persisted instrument
     */
    @Transactional
    public MarketInstrumentEntity createInstrument(String symbol,
                                                   String displayName,
                                                   InstrumentType type,
                                                   boolean enabled) {
        String canonicalSymbol = normalizeRequired(symbol);
        ensureInstrumentSymbolAvailable(canonicalSymbol, null);

        MarketInstrumentEntity entity = new MarketInstrumentEntity();
        entity.setSymbol(canonicalSymbol);
        entity.setDisplayName(displayName);
        entity.setType(type);
        entity.setEnabled(enabled);
        return marketInstrumentRepository.save(entity);
    }

    /**
     * Updates an existing canonical instrument.
     *
     * @param instrumentId instrument identifier
     * @param symbol input symbol
     * @param displayName display name
     * @param type instrument type
     * @param enabled enabled flag
     * @return updated instrument
     */
    @Transactional
    public MarketInstrumentEntity updateInstrument(UUID instrumentId,
                                                   String symbol,
                                                   String displayName,
                                                   InstrumentType type,
                                                   boolean enabled) {
        MarketInstrumentEntity entity = getInstrument(instrumentId);
        String canonicalSymbol = normalizeRequired(symbol);
        ensureInstrumentSymbolAvailable(canonicalSymbol, instrumentId);
        entity.setSymbol(canonicalSymbol);
        entity.setDisplayName(displayName);
        entity.setType(type);
        entity.setEnabled(enabled);
        return marketInstrumentRepository.save(entity);
    }

    /**
     * Soft-deletes an instrument by disabling it.
     *
     * @param instrumentId instrument identifier
     */
    @Transactional
    public void disableInstrument(UUID instrumentId) {
        MarketInstrumentEntity entity = getInstrument(instrumentId);
        entity.setEnabled(false);
        marketInstrumentRepository.save(entity);
    }

    /**
     * Lists mappings for an instrument ordered by priority.
     *
     * @param instrumentId instrument identifier
     * @return mappings for the instrument
     */
    public List<MarketProviderMappingEntity> getMappings(UUID instrumentId) {
        getInstrument(instrumentId);
        return marketProviderMappingRepository.findByInstrument_IdOrderByPriorityAscIdAsc(instrumentId);
    }

    /**
     * Finds a mapping by identifier.
     *
     * @param mappingId mapping identifier
     * @return matching mapping
     */
    public MarketProviderMappingEntity getMapping(UUID mappingId) {
        return marketProviderMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument mapping not found: " + mappingId));
    }

    /**
     * Creates a provider mapping for an instrument.
     *
     * @param instrumentId instrument identifier
     * @param source provider source
     * @param externalSymbol provider symbol
     * @param priority mapping priority
     * @param refreshIntervalMinutes refresh interval in minutes
     * @param historyStartDate history lower bound
     * @param enabled enabled flag
     * @return persisted mapping
     */
    @Transactional
    public MarketProviderMappingEntity createMapping(UUID instrumentId,
                                                     DataSource source,
                                                     String externalSymbol,
                                                     int priority,
                                                     int refreshIntervalMinutes,
                                                     LocalDate historyStartDate,
                                                     boolean enabled) {
        MarketInstrumentEntity instrument = getInstrument(instrumentId);
        String normalizedExternalSymbol = normalizeExternalSymbol(externalSymbol);
        ensureExternalSymbolAvailable(source, normalizedExternalSymbol, null);

        MarketProviderMappingEntity entity = new MarketProviderMappingEntity();
        entity.setInstrument(instrument);
        entity.setSource(source);
        entity.setExternalSymbol(normalizedExternalSymbol);
        entity.setPriority(priority);
        entity.setRefreshIntervalMinutes(Math.max(refreshIntervalMinutes, 1));
        entity.setHistoryStartDate(historyStartDate);
        entity.setEnabled(enabled);
        entity.setLastRefreshStatus(MappingRefreshStatus.PENDING);
        return marketProviderMappingRepository.save(entity);
    }

    /**
     * Updates an existing provider mapping.
     *
     * @param instrumentId instrument identifier
     * @param mappingId mapping identifier
     * @param source provider source
     * @param externalSymbol provider symbol
     * @param priority mapping priority
     * @param refreshIntervalMinutes refresh interval in minutes
     * @param historyStartDate history lower bound
     * @param enabled enabled flag
     * @return updated mapping
     */
    @Transactional
    public MarketProviderMappingEntity updateMapping(UUID instrumentId,
                                                     UUID mappingId,
                                                     DataSource source,
                                                     String externalSymbol,
                                                     int priority,
                                                     int refreshIntervalMinutes,
                                                     LocalDate historyStartDate,
                                                     boolean enabled) {
        MarketInstrumentEntity instrument = getInstrument(instrumentId);
        MarketProviderMappingEntity entity = getMapping(mappingId);
        if (!entity.getInstrument().getId().equals(instrument.getId())) {
            throw new ResourceNotFoundException("Instrument mapping not found: " + mappingId);
        }

        String normalizedExternalSymbol = normalizeExternalSymbol(externalSymbol);
        ensureExternalSymbolAvailable(source, normalizedExternalSymbol, mappingId);

        entity.setInstrument(instrument);
        entity.setSource(source);
        entity.setExternalSymbol(normalizedExternalSymbol);
        entity.setPriority(priority);
        entity.setRefreshIntervalMinutes(Math.max(refreshIntervalMinutes, 1));
        entity.setHistoryStartDate(historyStartDate);
        entity.setEnabled(enabled);
        return marketProviderMappingRepository.save(entity);
    }

    /**
     * Soft-deletes a provider mapping by disabling it.
     *
     * @param instrumentId instrument identifier
     * @param mappingId mapping identifier
     */
    @Transactional
    public void disableMapping(UUID instrumentId, UUID mappingId) {
        MarketProviderMappingEntity entity = getMapping(mappingId);
        if (!entity.getInstrument().getId().equals(instrumentId)) {
            throw new ResourceNotFoundException("Instrument mapping not found: " + mappingId);
        }
        entity.setEnabled(false);
        marketProviderMappingRepository.save(entity);
    }

    /**
     * Resolves all active instrument definitions.
     *
     * @return active instrument definitions
     */
    public List<InstrumentDefinition> getAll() {
        if (marketProviderMappingRepository == null) {
            return seededDefinitions;
        }
        return buildInstrumentDefinitions(marketProviderMappingRepository.findByEnabledTrueAndInstrument_EnabledTrueOrderBySourceAscPriorityAscIdAsc());
    }

    /**
     * Resolves active instrument definitions for a source.
     *
     * @param source provider source
     * @return active definitions for the source
     */
    public List<InstrumentDefinition> getBySource(DataSource source) {
        return buildInstrumentDefinitions(resolveEntities(source));
    }

    /**
     * Resolves a canonical instrument definition by symbol.
     *
     * @param symbol input symbol
     * @return matching definition if present
     */
    public Optional<InstrumentDefinition> getBySymbol(String symbol) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(null);
        if (canonicalSymbol == null) {
            return Optional.empty();
        }
        return getAll().stream()
                .filter(definition -> definition.symbol().equals(canonicalSymbol))
                .findFirst();
    }

    /**
     * Resolves a canonical definition by provider code.
     *
     * @param source provider source
     * @param providerCode provider symbol
     * @return matching definition if present
     */
    public Optional<InstrumentDefinition> getByProviderCode(DataSource source, String providerCode) {
        String normalizedExternalSymbol = normalizeExternalSymbol(providerCode);
        return resolveMappings(source).mappings().stream()
                .filter(mapping -> mapping.providerSymbol().equalsIgnoreCase(normalizedExternalSymbol))
                .findFirst()
                .flatMap(mapping -> getBySymbol(mapping.symbol()));
    }

    /**
     * Lists mappings that are currently due for refresh.
     *
     * @param now reference time
     * @return due mappings
     */
    public List<ResolvedMapping> getDueMappings(Instant now) {
        if (marketProviderMappingRepository == null) {
            return List.of();
        }
        return marketProviderMappingRepository.findDueMappings(now).stream()
                .map(this::toResolvedMapping)
                .toList();
    }

    /**
     * Lists provider mappings that are currently due for refresh for a single source.
     *
     * @param source provider source
     * @param now reference time
     * @return due mappings for the source
     */
    public List<ResolvedMapping> getDueMappings(DataSource source, Instant now) {
        if (marketProviderMappingRepository == null) {
            return List.of();
        }
        return marketProviderMappingRepository.findDueMappingsBySource(source.name(), now).stream()
                .map(this::toResolvedMapping)
                .toList();
    }

    /**
     * Marks mappings for a refresh request as successful.
     *
     * @param source provider source
     * @param refreshedSymbols canonical symbols refreshed successfully
     * @param refreshedAt success timestamp
     */
    @Transactional
    public void markRefreshSuccess(DataSource source, Set<String> refreshedSymbols, Instant refreshedAt) {
        if (marketProviderMappingRepository == null || refreshedSymbols == null || refreshedSymbols.isEmpty()) {
            return;
        }

        Instant effectiveTime = refreshedAt == null ? clock.instant() : refreshedAt;
        for (String symbol : refreshedSymbols) {
            marketProviderMappingRepository
                    .findFirstByInstrument_SymbolAndSourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(symbol, source)
                    .ifPresent(mapping -> {
                        mapping.setLastRefreshedAt(effectiveTime);
                        mapping.setLastRefreshStatus(MappingRefreshStatus.SUCCESS);
                        mapping.setLastFailedAt(null);
                        mapping.setLastFailureReason(null);
                        marketProviderMappingRepository.save(mapping);
                    });
        }
    }

    /**
     * Marks a mapping refresh as successful.
     *
     * @param mappingId mapping identifier
     * @param refreshedAt success timestamp
     */
    @Transactional
    public void markRefreshSuccess(UUID mappingId, Instant refreshedAt) {
        if (marketProviderMappingRepository == null || mappingId == null) {
            return;
        }
        marketProviderMappingRepository.findById(mappingId).ifPresent(mapping -> {
            mapping.setLastRefreshedAt(refreshedAt == null ? clock.instant() : refreshedAt);
            mapping.setLastRefreshStatus(MappingRefreshStatus.SUCCESS);
            mapping.setLastFailedAt(null);
            mapping.setLastFailureReason(null);
            marketProviderMappingRepository.save(mapping);
        });
    }

    /**
     * Marks mappings for a refresh request as failed.
     *
     * @param source provider source
     * @param attemptedSymbols canonical symbols attempted during refresh
     */
    @Transactional
    public void markRefreshFailure(DataSource source, Set<String> attemptedSymbols) {
        markRefreshFailure(source, attemptedSymbols, "Refresh failed");
    }

    /**
     * Marks mappings for a refresh request as failed.
     *
     * @param source provider source
     * @param attemptedSymbols canonical symbols attempted during refresh
     * @param reason failure reason
     */
    @Transactional
    public void markRefreshFailure(DataSource source, Set<String> attemptedSymbols, String reason) {
        if (marketProviderMappingRepository == null || attemptedSymbols == null || attemptedSymbols.isEmpty()) {
            return;
        }

        for (String symbol : attemptedSymbols) {
            marketProviderMappingRepository
                    .findFirstByInstrument_SymbolAndSourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(symbol, source)
                    .ifPresent(mapping -> {
                        mapping.setLastRefreshStatus(MappingRefreshStatus.FAILED);
                        mapping.setLastFailedAt(clock.instant());
                        mapping.setLastFailureReason(reason);
                        marketProviderMappingRepository.save(mapping);
                    });
        }
    }

    /**
     * Marks a mapping refresh as failed.
     *
     * @param mappingId mapping identifier
     * @param reason failure reason
     */
    @Transactional
    public void markRefreshFailed(UUID mappingId, String reason) {
        if (marketProviderMappingRepository == null || mappingId == null) {
            return;
        }
        marketProviderMappingRepository.findById(mappingId).ifPresent(mapping -> {
            mapping.setLastRefreshStatus(MappingRefreshStatus.FAILED);
            mapping.setLastFailedAt(clock.instant());
            mapping.setLastFailureReason(reason);
            marketProviderMappingRepository.save(mapping);
        });
    }

    /**
     * Resolves the preferred mapping for a symbol and source.
     *
     * @param source provider source
     * @param symbol canonical symbol
     * @return preferred active mapping if present
     */
    public Optional<ResolvedMapping> findPreferredMapping(DataSource source, String symbol) {
        if (marketProviderMappingRepository == null) {
            return resolveMappings(source).mappings().stream()
                    .filter(mapping -> mapping.symbol().equals(normalizeRequired(symbol)))
                    .findFirst();
        }

        String canonicalSymbol = normalizeRequired(symbol);
        return marketProviderMappingRepository
                .findFirstByInstrument_SymbolAndSourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(canonicalSymbol, source)
                .map(this::toResolvedMapping);
    }

    private List<MarketProviderMappingEntity> resolveEntities(DataSource source) {
        if (marketProviderMappingRepository == null) {
            return List.of();
        }
        return marketProviderMappingRepository.findBySourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(source);
    }

    private List<InstrumentDefinition> buildInstrumentDefinitions(List<MarketProviderMappingEntity> mappings) {
        Map<String, InstrumentDefinitionBuilder> builders = new LinkedHashMap<>();
        for (MarketProviderMappingEntity mapping : mappings) {
            MarketInstrumentEntity instrument = mapping.getInstrument();
            builders.computeIfAbsent(instrument.getSymbol(), ignored -> new InstrumentDefinitionBuilder(
                    instrument.getSymbol(),
                    instrument.getDisplayName(),
                    instrument.getType()
            )).providerCodes.put(mapping.getSource(), mapping.getExternalSymbol());
        }

        return builders.values().stream()
                .map(InstrumentDefinitionBuilder::build)
                .sorted(Comparator.comparing(InstrumentDefinition::symbol))
                .toList();
    }

    private ResolvedMapping toResolvedMapping(MarketProviderMappingEntity entity) {
        return new ResolvedMapping(
                entity.getId(),
                entity.getSource(),
                entity.getInstrument().getSymbol(),
                entity.getInstrument().getDisplayName(),
                entity.getInstrument().getType(),
                inferCurrency(entity.getInstrument().getSymbol(), entity.getInstrument().getType(), entity.getSource()),
                entity.getExternalSymbol(),
                entity.getPriority(),
                entity.getRefreshIntervalMinutes(),
                entity.getHistoryStartDate(),
                entity.getLastRefreshedAt(),
                entity.getLastRefreshStatus(),
                entity.getLastFailedAt(),
                entity.getLastFailureReason()
        );
    }

    private List<ResolvedMapping> seededMappings(DataSource source) {
        List<ResolvedMapping> mappings = new ArrayList<>();
        int priority = 1;
        for (InstrumentDefinition definition : seededDefinitions) {
            String providerSymbol = definition.providerCodes().get(source);
            if (providerSymbol == null || providerSymbol.isBlank()) {
                continue;
            }
            mappings.add(new ResolvedMapping(
                    UUID.randomUUID(),
                    source,
                    definition.symbol(),
                    definition.displayName(),
                    definition.instrumentType(),
                    definition.currency(),
                    providerSymbol,
                    priority++,
                    5,
                    null,
                    null,
                    MappingRefreshStatus.PENDING,
                    null,
                    null
            ));
        }
        return List.copyOf(mappings);
    }

    private void ensureInstrumentSymbolAvailable(String symbol, UUID currentId) {
        marketInstrumentRepository.findBySymbol(symbol)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Instrument already exists for symbol: " + symbol);
                });
    }

    private void ensureExternalSymbolAvailable(DataSource source, String externalSymbol, UUID currentId) {
        marketProviderMappingRepository.findFirstBySourceAndExternalSymbolAndEnabledTrueAndInstrument_EnabledTrue(source, externalSymbol)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Provider mapping already exists for source " + source + " and symbol " + externalSymbol
                    );
                });
    }

    private String normalizeRequired(String symbol) {
        return symbolNormalizer.normalize(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Symbol cannot be blank"));
    }

    private String normalizeExternalSymbol(String externalSymbol) {
        if (externalSymbol == null || externalSymbol.isBlank()) {
            throw new IllegalArgumentException("External symbol cannot be blank");
        }
        return externalSymbol.trim().toUpperCase();
    }

    private String inferCurrency(String symbol, InstrumentType type, DataSource source) {
        if (source == DataSource.BIST || source == DataSource.EVDS || type == InstrumentType.FOREX) {
            return "TRY";
        }
        if (symbol != null && symbol.endsWith("USDT")) {
            return "USDT";
        }
        return null;
    }

    /**
     * Immutable view of a canonical instrument and all of its provider symbols.
     *
     * @param symbol canonical symbol
     * @param displayName display name
     * @param instrumentType canonical type
     * @param currency inferred display currency
     * @param providerCodes provider symbol map
     */
    public record InstrumentDefinition(
            String symbol,
            String displayName,
            InstrumentType instrumentType,
            String currency,
            Map<DataSource, String> providerCodes
    ) {
    }

    /**
     * Immutable view of a single active provider mapping.
     *
     * @param source provider source
     * @param symbol canonical symbol
     * @param displayName display name
     * @param instrumentType canonical type
     * @param currency inferred display currency
     * @param providerSymbol provider symbol
     * @param priority priority order
     * @param refreshIntervalMinutes refresh interval in minutes
     * @param historyStartDate history lower bound
     * @param lastRefreshedAt last successful refresh time
     * @param lastRefreshStatus last refresh status
     */
    public record ResolvedMapping(
            UUID mappingId,
            DataSource source,
            String symbol,
            String displayName,
            InstrumentType instrumentType,
            String currency,
            String providerSymbol,
            int priority,
            int refreshIntervalMinutes,
            LocalDate historyStartDate,
            Instant lastRefreshedAt,
            MappingRefreshStatus lastRefreshStatus,
            Instant lastFailedAt,
            String lastFailureReason
    ) {
    }

    /**
     * Immutable resolution result for a provider source.
     *
     * @param source provider source
     * @param mappings active mappings for the source
     */
    public record Resolution(
            DataSource source,
            List<ResolvedMapping> mappings
    ) {
        /**
         * Normalizes the resolution payload.
         *
         * @param source provider source
         * @param mappings active mappings
         */
        public Resolution {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
        }
    }

    private static final class InstrumentDefinitionBuilder {
        private final String symbol;
        private final String displayName;
        private final InstrumentType instrumentType;
        private final Map<DataSource, String> providerCodes = new EnumMap<>(DataSource.class);

        private InstrumentDefinitionBuilder(String symbol, String displayName, InstrumentType instrumentType) {
            this.symbol = symbol;
            this.displayName = displayName;
            this.instrumentType = instrumentType;
        }

        private InstrumentDefinition build() {
            String currency = providerCodes.containsKey(DataSource.BIST) || providerCodes.containsKey(DataSource.EVDS)
                    || instrumentType == InstrumentType.FOREX
                    ? "TRY"
                    : symbol.endsWith("USDT") ? "USDT" : null;
            return new InstrumentDefinition(symbol, displayName, instrumentType, currency, Map.copyOf(providerCodes));
        }
    }
}
