package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
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

    Optional<MarketInstrument> findByInstrumentCodeIgnoreCaseAndSourceName(String instrumentCode, SourceName sourceName);

    Optional<MarketInstrument> findFirstByInstrumentCodeIgnoreCaseOrderByCreatedAtAsc(String instrumentCode);

    Optional<MarketInstrument> findFirstByInstrumentCodeIgnoreCaseAndInstrumentTypeOrderByCreatedAtAsc(
            String instrumentCode,
            InstrumentType instrumentType
    );

    Optional<MarketInstrument> findByInstrumentCodeAndSourceName(String instrumentCode, SourceName sourceName);

    List<MarketInstrument> findAllByInstrumentCodeInAndSourceName(List<String> instrumentCodes, SourceName sourceName);

    List<MarketInstrument> findAllByInstrumentTypeAndSourceName(InstrumentType instrumentType, SourceName sourceName);

    Optional<MarketInstrument> findFirstByInstrumentCodeAndInstrumentTypeOrderByCreatedAtAsc(String code, InstrumentType type);

    boolean existsByInstrumentCodeIgnoreCase(String instrumentCode);

    @Query("""
            select instrument
            from MarketInstrument instrument
            where instrument.instrumentCode is not null
              and trim(instrument.instrumentCode) <> ''
            """)
    List<MarketInstrument> findAllWithNonBlankInstrumentCode();
}
