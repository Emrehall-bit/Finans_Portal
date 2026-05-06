package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketInstrumentRepository extends JpaRepository<MarketInstrumentEntity, Long> {

    Optional<MarketInstrumentEntity> findBySymbolIgnoreCase(String symbol);
}
