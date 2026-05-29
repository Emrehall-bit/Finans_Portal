package com.emrehalli.financeportal.company.persistence;

import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyFinancialValueRepository extends JpaRepository<CompanyFinancialValue, Long> {

    List<CompanyFinancialValue> findByReportId(Long reportId);

    @Query("SELECT v FROM CompanyFinancialValue v WHERE v.report.id IN :reportIds ORDER BY v.report.id, v.id")
    List<CompanyFinancialValue> findByReportIdIn(@Param("reportIds") List<Long> reportIds);

    boolean existsByReportIdAndItemKey(Long reportId, String itemKey);

    Optional<CompanyFinancialValue> findByReportIdAndItemKey(Long reportId, String itemKey);
}




