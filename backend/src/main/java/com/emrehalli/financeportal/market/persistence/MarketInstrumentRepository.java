package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repository for market instruments.
 */
public interface MarketInstrumentRepository extends JpaRepository<MarketInstrument, Long> {

    Optional<MarketInstrument> findByInstrumentCodeIgnoreCase(String instrumentCode);

    Optional<MarketInstrument> findByInstrumentCodeAndSourceName(String instrumentCode, SourceName sourceName);

    boolean existsByInstrumentCodeIgnoreCase(String instrumentCode);

    @Query("""
            select instrument
            from MarketInstrument instrument
            where instrument.instrumentCode is not null
              and trim(instrument.instrumentCode) <> ''
            """)
    List<MarketInstrument> findAllWithNonBlankInstrumentCode();
}
