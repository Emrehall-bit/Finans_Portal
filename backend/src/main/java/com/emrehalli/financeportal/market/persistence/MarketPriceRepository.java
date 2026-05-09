package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for market prices.
 */
public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findTopByInstrumentOrderByPriceTimestampDesc(MarketInstrument instrument);

    List<MarketPrice> findByInstrumentAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            LocalDateTime start,
            LocalDateTime end
    );
}
