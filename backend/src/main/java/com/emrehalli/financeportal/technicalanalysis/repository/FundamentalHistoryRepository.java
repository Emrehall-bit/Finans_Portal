package com.emrehalli.financeportal.technicalanalysis.repository;

import com.emrehalli.financeportal.technicalanalysis.entity.FundamentalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundamentalHistoryRepository extends JpaRepository<FundamentalHistory, Long> {

    List<FundamentalHistory> findTop8ByInstrumentIdOrderByPeriodDesc(Long instrumentId);

    List<FundamentalHistory> findByInstrumentIdOrderByPeriodDesc(Long instrumentId);
}

