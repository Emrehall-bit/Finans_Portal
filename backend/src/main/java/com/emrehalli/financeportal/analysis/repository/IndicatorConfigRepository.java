package com.emrehalli.financeportal.analysis.repository;

import com.emrehalli.financeportal.analysis.entity.IndicatorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicatorConfigRepository extends JpaRepository<IndicatorConfig, Long> {

    List<IndicatorConfig> findByUserIdAndInstrumentIdAndIsActiveTrue(Long userId, Long instrumentId);

    List<IndicatorConfig> findByUserIdAndInstrumentId(Long userId, Long instrumentId);

    void deleteByIdAndUserId(Long id, Long userId);
}
