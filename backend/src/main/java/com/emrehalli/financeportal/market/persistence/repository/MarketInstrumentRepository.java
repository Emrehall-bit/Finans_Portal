package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for canonical market instruments.
 */
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrumentEntity, UUID> {

    /**
     * Finds a canonical instrument by normalized symbol.
     *
     * @param symbol normalized instrument symbol
     * @return matching instrument if present
     */
    Optional<MarketInstrumentEntity> findBySymbol(String symbol);

    /**
     * Lists instruments filtered by type and enabled flag.
     *
     * @param type instrument type filter
     * @param enabled enabled flag filter
     * @return matching instruments
     */
    List<MarketInstrumentEntity> findByTypeAndEnabled(InstrumentType type, boolean enabled);

    /**
     * Lists instruments filtered only by enabled flag.
     *
     * @param enabled enabled flag filter
     * @return matching instruments
     */
    List<MarketInstrumentEntity> findByEnabled(boolean enabled);

    /**
     * Lists instruments filtered only by type.
     *
     * @param type instrument type filter
     * @return matching instruments
     */
    List<MarketInstrumentEntity> findByType(InstrumentType type);
}
