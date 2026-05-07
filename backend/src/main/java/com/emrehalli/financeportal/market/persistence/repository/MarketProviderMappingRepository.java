package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for provider-specific instrument mappings.
 */
public interface MarketProviderMappingRepository extends JpaRepository<MarketProviderMappingEntity, UUID> {

    /**
     * Lists all enabled mappings whose parent instrument is also enabled.
     *
     * @return active mappings
     */
    @EntityGraph(attributePaths = "instrument")
    List<MarketProviderMappingEntity> findByEnabledTrueAndInstrument_EnabledTrueOrderBySourceAscPriorityAscIdAsc();

    /**
     * Lists enabled mappings for a provider whose parent instrument is enabled.
     *
     * @param source provider source
     * @return active provider mappings
     */
    @EntityGraph(attributePaths = "instrument")
    List<MarketProviderMappingEntity> findBySourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(
            DataSource source
    );

    /**
     * Lists mappings belonging to a specific instrument.
     *
     * @param instrumentId instrument identifier
     * @return mappings for the instrument
     */
    @EntityGraph(attributePaths = "instrument")
    List<MarketProviderMappingEntity> findByInstrument_IdOrderByPriorityAscIdAsc(UUID instrumentId);

    /**
     * Finds the preferred active mapping for a canonical symbol.
     *
     * @param symbol canonical symbol
     * @return preferred mapping if present
     */
    @EntityGraph(attributePaths = "instrument")
    Optional<MarketProviderMappingEntity> findFirstByInstrument_SymbolAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(
            String symbol
    );

    /**
     * Finds the preferred active mapping for a canonical symbol and provider.
     *
     * @param symbol canonical symbol
     * @param source provider source
     * @return preferred mapping if present
     */
    @EntityGraph(attributePaths = "instrument")
    Optional<MarketProviderMappingEntity> findFirstByInstrument_SymbolAndSourceAndEnabledTrueAndInstrument_EnabledTrueOrderByPriorityAscIdAsc(
            String symbol,
            DataSource source
    );

    /**
     * Finds an active mapping by provider source and external symbol.
     *
     * @param source provider source
     * @param externalSymbol provider symbol
     * @return matching mapping if present
     */
    @EntityGraph(attributePaths = "instrument")
    Optional<MarketProviderMappingEntity> findFirstBySourceAndExternalSymbolAndEnabledTrueAndInstrument_EnabledTrue(
            DataSource source,
            String externalSymbol
    );

    @EntityGraph(attributePaths = "instrument")
    Optional<MarketProviderMappingEntity> findFirstByInstrument_IdAndSourceAndExternalSymbolAndEnabledTrueAndInstrument_EnabledTrue(
            UUID instrumentId,
            DataSource source,
            String externalSymbol
    );

    /**
     * Finds all mappings that are due for refresh.
     *
     * @param now reference time
     * @return due mappings
     */
    @Query(
            value = """
                    select mapping.*
                    from market_provider_mappings mapping
                    join market_instruments instrument on instrument.id = mapping.instrument_id
                    where mapping.enabled = true
                      and instrument.enabled = true
                      and (
                            mapping.last_refreshed_at is null
                            or mapping.last_refreshed_at + make_interval(mins => mapping.refresh_interval_minutes) <= :now
                          )
                    order by mapping.source asc, mapping.priority asc, mapping.id asc
                    """,
            nativeQuery = true
    )
    List<MarketProviderMappingEntity> findDueMappings(@Param("now") Instant now);

    /**
     * Finds due mappings for a specific source.
     *
     * @param source provider source
     * @param now reference time
     * @return due mappings for the source
     */
    @Query(
            value = """
                    select mapping.*
                    from market_provider_mappings mapping
                    join market_instruments instrument on instrument.id = mapping.instrument_id
                    where mapping.enabled = true
                      and instrument.enabled = true
                      and mapping.source = :source
                      and (
                            mapping.last_refreshed_at is null
                            or mapping.last_refreshed_at + make_interval(mins => mapping.refresh_interval_minutes) <= :now
                          )
                    order by mapping.priority asc, mapping.id asc
                    """,
            nativeQuery = true
    )
    List<MarketProviderMappingEntity> findDueMappingsBySource(@Param("source") String source, @Param("now") Instant now);
}
