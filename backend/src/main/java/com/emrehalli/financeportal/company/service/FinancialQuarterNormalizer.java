package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.enums.ParseStatus;
import com.emrehalli.financeportal.company.parser.FinancialItemKey;
import com.emrehalli.financeportal.company.repository.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.repository.CompanyFinancialValueRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Service
public class FinancialQuarterNormalizer {

    private static final Set<FinancialItemKey> QUARTERLY_ITEMS = Set.of(
            FinancialItemKey.HASILAT,
            FinancialItemKey.NET_DONEM_KARI,
            FinancialItemKey.BRUT_KAR,
            FinancialItemKey.FAVOK
    );

    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;

    public FinancialQuarterNormalizer(CompanyFinancialReportRepository reportRepository,
                                      CompanyFinancialValueRepository valueRepository) {
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
    }

    public BigDecimal getQuarterlyValue(Long companyId, FinancialItemKey itemKey, Integer year, Integer quarter) {
        if (companyId == null || itemKey == null || year == null || quarter == null) {
            return null;
        }

        BigDecimal current = getEffectiveValue(companyId, itemKey, year, quarter);
        if (current == null || quarter <= 1 || !QUARTERLY_ITEMS.contains(itemKey)) {
            return current;
        }

        BigDecimal previousQuarter = getEffectiveValue(companyId, itemKey, year, quarter - 1);
        if (previousQuarter == null) {
            return null;
        }
        return current.subtract(previousQuarter);
    }

    public BigDecimal getEffectiveValue(Long companyId, FinancialItemKey itemKey, Integer year, Integer quarter) {
        return findReport(companyId, year, quarter)
                .flatMap(report -> valueRepository.findByReportIdAndItemKey(report.getId(), itemKey.name()))
                .map(this::effectiveValue)
                .orElse(null);
    }

    public BigDecimal getTtmValue(Long companyId, FinancialItemKey itemKey, Integer year, Integer quarter) {
        if (companyId == null || itemKey == null || year == null || quarter == null) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        int collected = 0;
        int cursorYear = year;
        int cursorQuarter = quarter;

        while (collected < 4) {
            BigDecimal quarterly = getQuarterlyValue(companyId, itemKey, cursorYear, cursorQuarter);
            if (quarterly == null) {
                return null;
            }
            total = total.add(quarterly);
            collected++;

            cursorQuarter--;
            if (cursorQuarter < 1) {
                cursorQuarter = 4;
                cursorYear--;
            }
        }

        return total;
    }

    public BigDecimal effectiveValue(CompanyFinancialValue value) {
        if (value == null || value.getValue() == null) {
            return null;
        }
        int unitMultiplier = value.getUnitMultiplier() != null ? value.getUnitMultiplier() : 1;
        BigDecimal baseValue = normalizeKapStoredValue(value.getValue(), unitMultiplier);
        return baseValue.multiply(BigDecimal.valueOf(unitMultiplier));
    }

    private Optional<CompanyFinancialReport> findReport(Long companyId, Integer year, Integer quarter) {
        return reportRepository.findTopByCompanyIdAndPeriodYearAndPeriodQuarterAndParseStatusOrderByPublishedAtDescIdDesc(
                companyId,
                year,
                quarter,
                ParseStatus.SUCCESS);
    }

    private BigDecimal normalizeKapStoredValue(BigDecimal value, int unitMultiplier) {
        if (unitMultiplier >= 1_000_000 && value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            return value.multiply(BigDecimal.valueOf(1000));
        }
        return value;
    }
}
