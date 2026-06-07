package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = """
            select distinct on (mp.instrument_id) mp.*
            from market_prices mp
            where mp.instrument_id in (:instrumentIds)
            order by mp.instrument_id, mp.price_timestamp desc, mp.id desc
            """, nativeQuery = true)
    List<MarketPrice> findLatestPricesForInstruments(@Param("instrumentIds") List<Long> instrumentIds);

    @Query(value = """
            select distinct on (mp.instrument_id) mp.*
            from market_prices mp
            join market_instruments mi on mi.id = mp.instrument_id
            where mp.source_name = :sourceName
              and mi.instrument_type = :instrumentType
            order by mp.instrument_id, mp.price_timestamp desc, mp.id desc
            """, nativeQuery = true)
    List<MarketPrice> findLatestBySourceNameAndInstrumentType(
            @Param("sourceName") String sourceName,
            @Param("instrumentType") String instrumentType
    );
}




