package com.emrehalli.financeportal.analysis.repository;

import com.emrehalli.financeportal.analysis.entity.FundamentalRatios;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FundamentalRatiosRepository extends JpaRepository<FundamentalRatios, Long> {

    Optional<FundamentalRatios> findTopByInstrumentIdOrderByCalculatedAtDesc(Long instrumentId);

    List<FundamentalRatios> findTop8ByInstrumentIdOrderByPeriodDesc(Long instrumentId);

    Optional<FundamentalRatios> findByInstrumentIdAndPeriod(Long instrumentId, String period);
}
