package com.emrehalli.financeportal.company.persistence;

import com.emrehalli.financeportal.company.domain.entity.CompanyRatio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompanyRatioRepository extends JpaRepository<CompanyRatio, Long> {

    List<CompanyRatio> findByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(String tickerCode);

    Optional<CompanyRatio> findTopByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(String tickerCode);

    Optional<CompanyRatio> findByCompanyIdAndReportId(Long companyId, Long reportId);

    @Query("select distinct cr.company.id from CompanyRatio cr")
    List<Long> findDistinctCompanyIds();
}

