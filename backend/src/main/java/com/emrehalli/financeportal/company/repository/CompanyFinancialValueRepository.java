package com.emrehalli.financeportal.company.repository;

import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyFinancialValueRepository extends JpaRepository<CompanyFinancialValue, Long> {

    List<CompanyFinancialValue> findByReportId(Long reportId);

    @Query("SELECT v FROM CompanyFinancialValue v WHERE v.report.id IN :reportIds ORDER BY v.report.id, v.id")
    List<CompanyFinancialValue> findByReportIdIn(@Param("reportIds") List<Long> reportIds);

    boolean existsByReportIdAndItemKey(Long reportId, String itemKey);
}
