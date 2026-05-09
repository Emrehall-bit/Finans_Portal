package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
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

    List<MarketPriceHistory> findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            IntervalType intervalType,
            LocalDateTime from,
            LocalDateTime to
    );

    List<MarketPriceHistory> findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
            MarketInstrument instrument,
            IntervalType intervalType,
            SourceName sourceName,
            LocalDateTime from,
            LocalDateTime to
    );

    List<MarketPriceHistory> findByInstrumentInAndIntervalTypeAndSourceNameAndPriceTimestampBetween(
            List<MarketInstrument> instruments,
            IntervalType intervalType,
            SourceName sourceName,
            LocalDateTime from,
            LocalDateTime to
    );

    boolean existsByInstrumentAndIntervalTypeAndPriceTimestamp(
            MarketInstrument instrument,
            IntervalType intervalType,
            LocalDateTime priceTimestamp
    );
}
