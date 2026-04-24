package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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
}
