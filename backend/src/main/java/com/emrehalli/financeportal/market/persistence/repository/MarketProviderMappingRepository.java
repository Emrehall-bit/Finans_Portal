package com.emrehalli.financeportal.market.persistence.repository;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketProviderMappingRepository extends JpaRepository<MarketProviderMappingEntity, Long> {

    @EntityGraph(attributePaths = "instrument")
    List<MarketProviderMappingEntity> findByProviderSourceAndEnabledTrueAndInstrument_ActiveTrueOrderByPriorityAscIdAsc(
            DataSource providerSource
    );
}
