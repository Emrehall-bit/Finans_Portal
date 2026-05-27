package com.emrehalli.financeportal.analysis.repository;

import com.emrehalli.financeportal.analysis.entity.CompanyFinancials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyFinancialsRepository extends JpaRepository<CompanyFinancials, Long> {

    List<CompanyFinancials> findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(Long instrumentId, String periodType);

    Optional<CompanyFinancials> findByInstrumentIdAndPeriodAndPeriodType(Long instrumentId, String period, String periodType);

    Optional<CompanyFinancials> findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(Long instrumentId, String periodType);
}
