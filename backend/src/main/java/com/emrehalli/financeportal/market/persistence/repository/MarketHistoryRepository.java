package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketHistoryRepository extends JpaRepository<MarketHistoryEntity, Long> {

    List<MarketHistoryEntity> findBySourceAndSymbolInAndPriceDateBetween(
            DataSource source,
            Collection<String> symbols,
            LocalDate startDate,
            LocalDate endDate
    );

    List<MarketHistoryEntity> findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    );

    List<MarketHistoryEntity> findBySymbolAndSourceAndPriceDateBetweenOrderByPriceDateAsc(
            String symbol,
            DataSource source,
            LocalDate startDate,
            LocalDate endDate
    );

    long countBySymbolAndSource(String symbol, DataSource source);

    Optional<MarketHistoryEntity> findTopBySymbolOrderByPriceDateDescIdDesc(String symbol);

    @Query("select min(m.priceDate) from MarketHistoryEntity m where m.symbol = :symbol and m.source = :source")
    LocalDate findMinPriceDateBySymbolAndSource(@Param("symbol") String symbol, @Param("source") DataSource source);

    @Query("select max(m.priceDate) from MarketHistoryEntity m where m.symbol = :symbol and m.source = :source")
    LocalDate findMaxPriceDateBySymbolAndSource(@Param("symbol") String symbol, @Param("source") DataSource source);

    @Query("select count(distinct m.closePrice) from MarketHistoryEntity m where m.symbol = :symbol and m.source = :source")
    long countDistinctClosePriceBySymbolAndSource(@Param("symbol") String symbol, @Param("source") DataSource source);
}
