package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketDataBackfillStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketDataBackfillStatusRepository extends JpaRepository<MarketDataBackfillStatusEntity, Long> {
    Optional<MarketDataBackfillStatusEntity> findByProviderSourceAndSymbol(DataSource providerSource, String symbol);
}
