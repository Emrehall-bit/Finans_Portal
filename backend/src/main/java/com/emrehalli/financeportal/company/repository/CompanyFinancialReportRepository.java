package com.emrehalli.financeportal.company.repository;

import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyFinancialReportRepository extends JpaRepository<CompanyFinancialReport, Long> {

    List<CompanyFinancialReport> findByCompanyTickerCodeIgnoreCaseOrderByPeriodYearDescPeriodQuarterDesc(String tickerCode);

    Optional<CompanyFinancialReport> findTopByCompanyTickerCodeIgnoreCaseOrderByPeriodYearDescPeriodQuarterDesc(String tickerCode);

    boolean existsByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(Long companyId, Integer periodYear, Integer periodQuarter, ReportType reportType);

    Optional<CompanyFinancialReport> findByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(Long companyId, Integer periodYear, Integer periodQuarter, ReportType reportType);

    boolean existsByCompanyIdAndPeriodYearAndPeriodQuarter(Long companyId, Integer periodYear, Integer periodQuarter);

    List<CompanyFinancialReport> findByCompanyTickerCodeIgnoreCaseAndParseStatus(String tickerCode, ParseStatus parseStatus);

    Optional<CompanyFinancialReport> findTopByCompanyIdAndPeriodYearAndPeriodQuarterAndParseStatusOrderByPublishedAtDescIdDesc(
            Long companyId,
            Integer periodYear,
            Integer periodQuarter,
            ParseStatus parseStatus);

    @Modifying
    @Query("UPDATE CompanyFinancialReport r SET r.parseStatus = :status, r.lastCheckedAt = :checkedAt WHERE r.id = :id")
    void updateParseStatus(Long id, ParseStatus status, OffsetDateTime checkedAt);

    @Query("SELECT r FROM CompanyFinancialReport r WHERE UPPER(r.company.tickerCode) = UPPER(:tickerCode) AND r.parseStatus IN :statuses ORDER BY r.periodYear DESC, r.periodQuarter DESC")
    List<CompanyFinancialReport> findEligibleReports(@Param("tickerCode") String tickerCode,
                                                     @Param("statuses") Collection<ParseStatus> statuses);
}
