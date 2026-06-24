package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
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
            """, nativeQuery = true)
    Optional<LocalDate> findTopDateBySymbolOrderByDateDesc(@Param("symbol") String symbol);

    @Query(value = """
            select cast(max(mph.price_timestamp) as date)
            from market_price_history mph
            join market_instruments mi on mi.id = mph.instrument_id
            where upper(mi.instrument_code) = upper(:symbol)
              and mi.instrument_type = 'STOCK'
              and mph.interval_type = 'ONE_DAY'
              and mph.source_name = :sourceName
            """, nativeQuery = true)
    Optional<LocalDate> findTopDateBySymbolAndSourceNameOrderByDateDesc(@Param("symbol") String symbol,
                                                                        @Param("sourceName") String sourceName);

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

    Optional<MarketPriceHistory> findTopByInstrumentAndIntervalTypeAndPriceTimestampLessThanOrderByPriceTimestampDesc(
            MarketInstrument instrument,
            IntervalType intervalType,
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

    @Query("""
            SELECT mi.instrumentCode AS code, MAX(mph.priceTimestamp) AS lastTimestamp
            FROM MarketPriceHistory mph
            JOIN mph.instrument mi
            WHERE mi.instrumentType = :instrumentType
              AND mi.sourceName    = :sourceName
              AND mph.intervalType = :intervalType
            GROUP BY mi.instrumentCode
            """)
    List<LastHistoryDateProjection> findLastDatePerInstrument(
            @Param("instrumentType") InstrumentType instrumentType,
            @Param("sourceName")     SourceName sourceName,
            @Param("intervalType")   IntervalType intervalType
    );

    @Query(value = """
            SELECT DISTINCT ON (mph.instrument_id) mph.*
            FROM market_price_history mph
            WHERE mph.instrument_id IN (:instrumentIds)
              AND mph.interval_type  = 'ONE_DAY'
              AND mph.source_name    = :sourceName
            ORDER BY mph.instrument_id, mph.price_timestamp DESC, mph.id DESC
            """, nativeQuery = true)
    List<MarketPriceHistory> findLatestDailyHistoriesForInstruments(
            @Param("instrumentIds") List<Long> instrumentIds,
            @Param("sourceName") String sourceName);

    @Query(value = """
            SELECT DISTINCT ON (mph.instrument_id) mph.*
            FROM market_price_history mph
            JOIN (
                SELECT DISTINCT ON (mph2.instrument_id)
                       mph2.instrument_id,
                       mph2.price_timestamp
                FROM market_price_history mph2
                WHERE mph2.instrument_id IN (:instrumentIds)
                  AND mph2.interval_type  = 'ONE_DAY'
                  AND mph2.source_name    = :sourceName
                ORDER BY mph2.instrument_id, mph2.price_timestamp DESC
            ) latest ON latest.instrument_id = mph.instrument_id
            WHERE mph.instrument_id IN (:instrumentIds)
              AND mph.interval_type  = 'ONE_DAY'
              AND mph.source_name    = :sourceName
              AND mph.price_timestamp < latest.price_timestamp
            ORDER BY mph.instrument_id, mph.price_timestamp DESC, mph.id DESC
            """, nativeQuery = true)
    List<MarketPriceHistory> findPreviousClosesForStockInstruments(
            @Param("instrumentIds") List<Long> instrumentIds,
            @Param("sourceName") String sourceName);
}

