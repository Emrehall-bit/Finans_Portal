package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for market price history records.
 */
public interface MarketPriceHistoryRepository extends JpaRepository<MarketPriceHistory, Long> {

    Optional<MarketPriceHistory> findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(
            MarketInstrument instrument,
            IntervalType intervalType
    );

    Optional<MarketPriceHistory> findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName
    );

    Optional<MarketPriceHistory> findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName
    );

    @Query(value = """
            select cast(max(mph.price_timestamp) as date)
            from market_price_history mph
            join market_instruments mi on mi.id = mph.instrument_id
            where upper(mi.instrument_code) = upper(:symbol)
              and mi.instrument_type = 'STOCK'
              and mph.interval_type = 'ONE_DAY'
              and mph.source_name = 'IS_YATIRIM'
            """, nativeQuery = true)
    Optional<LocalDate> findTopDateBySymbolOrderByDateDesc(@Param("symbol") String symbol);

    @Query(value = """
            select mph.*
            from market_price_history mph
            join market_instruments mi on mi.id = mph.instrument_id
            where upper(mi.instrument_code) = upper(:symbol)
              and mi.instrument_type = 'STOCK'
              and cast(mph.price_timestamp as date) between :startDate and :endDate
            order by mph.price_timestamp asc
            """, nativeQuery = true)
    List<MarketPriceHistory> findBySymbolAndDateBetweenOrderByDateAsc(@Param("symbol") String symbol,
                                                                      @Param("startDate") LocalDate startDate,
                                                                      @Param("endDate") LocalDate endDate);

    Optional<MarketPriceHistory> findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestamp(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName,
            Instant priceTimestamp
    );

    Optional<MarketPriceHistory> findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName,
            Instant priceTimestamp
    );

    List<MarketPriceHistory> findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            IntervalType intervalType,
            Instant from,
            Instant to
    );

    List<MarketPriceHistory> findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName,
            Instant from,
            Instant to
    );

    List<MarketPriceHistory> findByInstrumentIdAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            Long instrumentId,
            Instant startTimestamp,
            Instant endTimestamp
    );

    List<MarketPriceHistory> findByInstrumentInAndIntervalTypeAndSourceNameAndPriceTimestampBetween(
            List<MarketInstrument> instruments,
            IntervalType intervalType,
            SourceName sourceName,
            Instant from,
            Instant to
    );

    @Query(value = """
            select distinct on (mph.instrument_id) mph.*
            from market_price_history mph
            join (
                select distinct on (mp.instrument_id) mp.instrument_id, mp.price_timestamp
                from market_prices mp
                where mp.instrument_id in (:instrumentIds)
                order by mp.instrument_id, mp.price_timestamp desc, mp.id desc
            ) latest on latest.instrument_id = mph.instrument_id
            where mph.instrument_id in (:instrumentIds)
              and mph.interval_type = 'ONE_DAY'
              and mph.source_name = 'TEFAS'
              and mph.price_timestamp < date_trunc('day', latest.price_timestamp)
            order by mph.instrument_id, mph.price_timestamp desc, mph.id desc
            """, nativeQuery = true)
    List<MarketPriceHistory> findPreviousClosesForInstruments(@Param("instrumentIds") List<Long> instrumentIds);

    boolean existsByInstrumentAndIntervalTypeAndPriceTimestamp(
            MarketInstrument instrument,
            IntervalType intervalType,
            Instant priceTimestamp
    );
}




