package com.emrehalli.financeportal.technicalanalysis.repository;

import com.emrehalli.financeportal.technicalanalysis.entity.CompanyFinancials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompanyFinancialsRepository extends JpaRepository<CompanyFinancials, Long> {

    List<CompanyFinancials> findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(Long instrumentId, String periodType);

    Optional<CompanyFinancials> findByInstrumentIdAndPeriodAndPeriodType(Long instrumentId, String period, String periodType);

    @Query("SELECT DISTINCT cf.instrument.id FROM CompanyFinancials cf WHERE cf.instrument.id IS NOT NULL")
    List<Long> findDistinctInstrumentIds();

}

