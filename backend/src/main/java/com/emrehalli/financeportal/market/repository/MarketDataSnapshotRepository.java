package com.emrehalli.financeportal.market.repository;

import com.emrehalli.financeportal.market.entity.MarketDataSnapshot;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketDataSnapshotRepository extends JpaRepository<MarketDataSnapshot, Long> {

    List<MarketDataSnapshot> findBySymbolIgnoreCaseOrderByFetchedAtDesc(String symbol);

    Optional<MarketDataSnapshot> findFirstBySymbolIgnoreCaseOrderByFetchedAtDesc(String symbol);

    List<MarketDataSnapshot> findBySymbolIgnoreCaseAndFetchedAtBetweenOrderByFetchedAtAsc(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MarketDataSnapshot> findByInstrumentTypeOrderByFetchedAtDesc(InstrumentType instrumentType);
}
