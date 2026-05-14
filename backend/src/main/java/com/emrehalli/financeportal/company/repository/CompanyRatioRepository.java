package com.emrehalli.financeportal.company.repository;

import com.emrehalli.financeportal.company.entity.CompanyRatio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRatioRepository extends JpaRepository<CompanyRatio, Long> {

    List<CompanyRatio> findByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(String tickerCode);

    Optional<CompanyRatio> findTopByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(String tickerCode);

    Optional<CompanyRatio> findByCompanyIdAndReportId(Long companyId, Long reportId);
}
