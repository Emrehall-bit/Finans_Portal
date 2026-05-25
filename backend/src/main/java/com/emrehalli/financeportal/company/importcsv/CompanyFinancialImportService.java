package com.emrehalli.financeportal.company.importcsv;

import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportError;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportResponse;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialValue;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.domain.enums.FinancialItemKey;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialValueRepository;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.company.service.CompanyRatioService;
import com.emrehalli.financeportal.company.support.FinancialReportUpsertSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanyFinancialImportService {

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioService ratioService;
    private final ManualFinancialCsvReader csvReader;
    private final FinancialReportUpsertSupport upsertSupport;

    public CompanyFinancialImportService(CompanyProfileRepository profileRepository,
                                         CompanyFinancialReportRepository reportRepository,
                                         CompanyFinancialValueRepository valueRepository,
                                         CompanyRatioService ratioService,
                                         ManualFinancialCsvReader csvReader,
                                         FinancialReportUpsertSupport upsertSupport) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioService = ratioService;
        this.csvReader = csvReader;
        this.upsertSupport = upsertSupport;
    }

    @Transactional
    public ManualFinancialImportResponse importCsv(MultipartFile file,
                                                   boolean dryRun,
                                                   boolean replaceExisting,
                                                   boolean recalculateRatios) {
        ManualFinancialCsvReader.CsvReadResult readResult = csvReader.read(file);
        List<ManualFinancialImportError> validationErrors = new ArrayList<>(readResult.errors());
        if (!validationErrors.isEmpty()) {
            return ManualFinancialImportResponse.builder()
                    .dryRun(dryRun)
                    .createdReports(0)
                    .updatedReports(0)
                    .createdValues(0)
                    .updatedValues(0)
                    .deletedStaleValues(0)
                    .recalculatedTickers(List.of())
                    .validationErrors(validationErrors)
                    .build();
        }

        ImportPreparation preparation = prepare(readResult.rows(), validationErrors);
        ImportCounters counters = new ImportCounters();
        counters.createdReports = preparation.createdReports();
        counters.updatedReports = preparation.updatedReports();
        counters.createdValues = preparation.createdValues();
        counters.updatedValues = preparation.updatedValues();
        counters.deletedStaleValues = replaceExisting ? preparation.deletedStaleValues() : 0;

        List<String> recalculatedTickers = new ArrayList<>();
        if (!dryRun) {
            applyImport(preparation.reportImports(), replaceExisting);
            if (recalculateRatios) {
                recalculatedTickers = recalculate(preparation.affectedTickers(), validationErrors);
            }
        }

        return ManualFinancialImportResponse.builder()
                .dryRun(dryRun)
                .createdReports(counters.createdReports)
                .updatedReports(counters.updatedReports)
                .createdValues(counters.createdValues)
                .updatedValues(counters.updatedValues)
                .deletedStaleValues(counters.deletedStaleValues)
                .recalculatedTickers(recalculatedTickers)
                .validationErrors(validationErrors)
                .build();
    }

    private ImportPreparation prepare(List<ManualFinancialCsvReader.CsvRow> rows,
                                      List<ManualFinancialImportError> validationErrors) {
        Map<String, CompanyProfile> companyCache = new LinkedHashMap<>();
        Map<ReportKey, PreparedReportImport> grouped = new LinkedHashMap<>();

        for (ManualFinancialCsvReader.CsvRow row : rows) {
            ValidatedRow validatedRow = validateRow(row, companyCache, validationErrors);
            if (validatedRow == null) {
                continue;
            }

            ReportKey reportKey = new ReportKey(
                    validatedRow.company().getId(),
                    validatedRow.periodYear(),
                    validatedRow.periodQuarter(),
                    validatedRow.reportType());

            PreparedReportImport reportImport = grouped.computeIfAbsent(reportKey, key ->
                    new PreparedReportImport(
                            validatedRow.company(),
                            validatedRow.periodYear(),
                            validatedRow.periodQuarter(),
                            validatedRow.reportType(),
                            validatedRow.publishedAt(),
                            validatedRow.sourceUrl(),
                            new LinkedHashMap<>()));

            reportImport.setPublishedAt(validatedRow.publishedAt());
            reportImport.setSourceUrl(validatedRow.sourceUrl());
            reportImport.items().put(validatedRow.itemKey(), new PreparedValueImport(
                    validatedRow.itemKey(),
                    validatedRow.rawLabel(),
                    validatedRow.value(),
                    validatedRow.currency(),
                    validatedRow.unitMultiplier()));
        }

        List<PreparedReportImport> reportImports = grouped.values().stream()
                .sorted(Comparator.comparing((PreparedReportImport report) -> report.company().getTickerCode())
                        .thenComparing(PreparedReportImport::periodYear)
                        .thenComparing(PreparedReportImport::periodQuarter))
                .toList();

        int createdReports = 0;
        int updatedReports = 0;
        int createdValues = 0;
        int updatedValues = 0;
        int deletedStaleValues = 0;
        Set<String> affectedTickers = new LinkedHashSet<>();

        for (PreparedReportImport reportImport : reportImports) {
            affectedTickers.add(reportImport.company().getTickerCode());

            Optional<CompanyFinancialReport> existingReport = reportRepository
                    .findByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(
                            reportImport.company().getId(),
                            reportImport.periodYear(),
                            reportImport.periodQuarter(),
                            reportImport.reportType());
            reportImport.setExistingReport(existingReport.orElse(null));
            if (existingReport.isPresent()) {
                updatedReports++;
            } else {
                createdReports++;
            }

            Map<String, CompanyFinancialValue> existingValues = existingReport
                    .map(report -> valueRepository.findByReportId(report.getId()))
                    .orElse(List.of())
                    .stream()
                    .collect(LinkedHashMap::new, (map, value) -> map.put(value.getItemKey(), value), Map::putAll);
            reportImport.setExistingValues(existingValues);

            for (PreparedValueImport item : reportImport.items().values()) {
                if (existingValues.containsKey(item.itemKey().name())) {
                    updatedValues++;
                } else {
                    createdValues++;
                }
            }

            Set<String> staleKeys = new LinkedHashSet<>(existingValues.keySet());
            staleKeys.removeAll(reportImport.items().keySet().stream().map(Enum::name).collect(LinkedHashSet::new, Set::add, Set::addAll));
            reportImport.setStaleItemKeys(staleKeys);
            deletedStaleValues += staleKeys.size();
        }

        return new ImportPreparation(
                reportImports,
                createdReports,
                updatedReports,
                createdValues,
                updatedValues,
                deletedStaleValues,
                new ArrayList<>(affectedTickers));
    }

    private ValidatedRow validateRow(ManualFinancialCsvReader.CsvRow row,
                                     Map<String, CompanyProfile> companyCache,
                                     List<ManualFinancialImportError> validationErrors) {
        String tickerCode = normalize(row.get("ticker_code"));
        String periodYearRaw = row.get("period_year");
        String reportTypeRaw = normalize(row.get("report_type"));
        String publishedAtRaw = row.get("published_at");
        String sourceUrlRaw = row.get("source_url");
        String itemKeyRaw = normalize(row.get("item_key"));
        String rawLabel = blankToNull(row.get("raw_label"));
        String valueRaw = row.get("value");
        String currencyRaw = normalize(row.get("currency"));
        String unitMultiplierRaw = row.get("unit_multiplier");

        CompanyProfile company = null;
        Integer periodYear = null;
        ReportType reportType = null;
        Integer periodQuarter = null;
        LocalDate publishedAt = null;
        FinancialItemKey itemKey = null;
        BigDecimal value = null;
        Integer unitMultiplier = 1;

        if (tickerCode == null) {
            validationErrors.add(error(row.lineNumber(), null, null, reportTypeRaw, itemKeyRaw, "ticker_code", row.get("ticker_code"), "ticker_code zorunlu."));
        } else {
            company = companyCache.computeIfAbsent(tickerCode, key -> profileRepository.findByTickerCodeIgnoreCase(key).orElse(null));
            if (company == null) {
                validationErrors.add(error(row.lineNumber(), tickerCode, null, reportTypeRaw, itemKeyRaw, "ticker_code", tickerCode, "Şirket company_profiles içinde bulunamadı."));
            }
        }

        try {
            periodYear = Integer.valueOf(periodYearRaw);
        } catch (Exception e) {
            validationErrors.add(error(row.lineNumber(), tickerCode, null, reportTypeRaw, itemKeyRaw, "period_year", periodYearRaw, "period_year parse edilemedi."));
        }

        if (reportTypeRaw == null) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, null, itemKeyRaw, "report_type", row.get("report_type"), "report_type zorunlu."));
        } else {
            try {
                reportType = ReportType.valueOf(reportTypeRaw);
                if (reportType == ReportType.Q4) {
                    validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "report_type", reportTypeRaw, "Yalnızca Q1, Q2, Q3, ANNUAL kabul edilir."));
                } else {
                    periodQuarter = mapPeriodQuarter(reportType);
                }
            } catch (IllegalArgumentException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "report_type", reportTypeRaw, "Geçersiz report_type."));
            }
        }

        if (publishedAtRaw != null && !publishedAtRaw.isBlank()) {
            try {
                publishedAt = LocalDate.parse(publishedAtRaw.trim());
            } catch (DateTimeParseException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "published_at", publishedAtRaw, "published_at YYYY-MM-DD formatında olmalı."));
            }
        }

        if (itemKeyRaw == null) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, null, "item_key", row.get("item_key"), "item_key zorunlu."));
        } else {
            try {
                itemKey = FinancialItemKey.valueOf(itemKeyRaw);
            } catch (IllegalArgumentException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "item_key", itemKeyRaw, "FinancialItemKey enum içinde bulunamadı."));
            }
        }

        if (valueRaw == null || valueRaw.isBlank()) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "value", valueRaw, "value zorunlu."));
        } else {
            try {
                value = new BigDecimal(valueRaw.trim());
            } catch (NumberFormatException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "value", valueRaw, "value parse edilemedi."));
            }
        }

        if (unitMultiplierRaw != null && !unitMultiplierRaw.isBlank()) {
            try {
                unitMultiplier = Integer.valueOf(unitMultiplierRaw.trim());
            } catch (NumberFormatException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "unit_multiplier", unitMultiplierRaw, "unit_multiplier parse edilemedi."));
            }
        }

        if (company == null || periodYear == null || reportType == null || periodQuarter == null || itemKey == null || value == null) {
            return null;
        }

        String sourceUrl = blankToNull(sourceUrlRaw);
        if (sourceUrl == null) {
            sourceUrl = "manual://csv";
        }

        String currency = blankToNull(currencyRaw);
        if (currency == null) {
            currency = "TRY";
        }

        return new ValidatedRow(
                company,
                periodYear,
                periodQuarter,
                reportType,
                publishedAt,
                sourceUrl,
                itemKey,
                rawLabel,
                value,
                currency.toUpperCase(Locale.ROOT),
                unitMultiplier);
    }

    private void applyImport(List<PreparedReportImport> reportImports,
                             boolean replaceExisting) {
        OffsetDateTime now = OffsetDateTime.now();
        for (PreparedReportImport reportImport : reportImports) {
            CompanyFinancialReport report = upsertSupport.upsertReport(
                    reportImport.company(),
                    reportImport.periodYear(),
                    reportImport.periodQuarter(),
                    reportImport.reportType(),
                    reportImport.sourceUrl(),
                    reportImport.publishedAt(),
                    ParseStatus.SUCCESS,
                    now
            ).report();

            Map<String, CompanyFinancialValue> existingValues = new LinkedHashMap<>(reportImport.existingValues());
            for (PreparedValueImport item : reportImport.items().values()) {
                upsertSupport.upsertValue(
                        report,
                        item.itemKey().name(),
                        item.rawLabel(),
                        item.value(),
                        item.currency(),
                        item.unitMultiplier(),
                        true
                );
            }

            if (replaceExisting && reportImport.existingReport() != null && !reportImport.staleItemKeys().isEmpty()) {
                upsertSupport.deleteMissingValues(report.getId(), reportImport.items().keySet().stream()
                        .map(Enum::name)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll));
            }
        }
    }

    private List<String> recalculate(List<String> tickers, List<ManualFinancialImportError> validationErrors) {
        List<String> recalculatedTickers = new ArrayList<>();
        for (String ticker : tickers) {
            try {
                var result = ratioService.calculateForTicker(ticker);
                if (result.isCalculated()) {
                    recalculatedTickers.add(ticker);
                } else {
                    validationErrors.add(ManualFinancialImportError.builder()
                            .tickerCode(ticker)
                            .fieldName("recalculateRatios")
                            .message(result.getFailedReason())
                            .build());
                }
            } catch (Exception e) {
                validationErrors.add(ManualFinancialImportError.builder()
                        .tickerCode(ticker)
                        .fieldName("recalculateRatios")
                        .message("Oran hesaplama hatası: " + e.getMessage())
                        .build());
            }
        }
        return recalculatedTickers;
    }

    private Integer mapPeriodQuarter(ReportType reportType) {
        return switch (reportType) {
            case Q1 -> 1;
            case Q2 -> 2;
            case Q3 -> 3;
            case ANNUAL -> 4;
            case Q4 -> null;
        };
    }

    private ManualFinancialImportError error(Integer lineNumber,
                                             String tickerCode,
                                             Integer periodYear,
                                             String reportType,
                                             String itemKey,
                                             String fieldName,
                                             String rawValue,
                                             String message) {
        return ManualFinancialImportError.builder()
                .lineNumber(lineNumber)
                .tickerCode(tickerCode)
                .periodYear(periodYear)
                .reportType(reportType)
                .itemKey(itemKey)
                .fieldName(fieldName)
                .rawValue(rawValue)
                .message(message)
                .build();
    }

    private String normalize(String value) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedRow(CompanyProfile company,
                                Integer periodYear,
                                Integer periodQuarter,
                                ReportType reportType,
                                LocalDate publishedAt,
                                String sourceUrl,
                                FinancialItemKey itemKey,
                                String rawLabel,
                                BigDecimal value,
                                String currency,
                                Integer unitMultiplier) {
    }

    private record ReportKey(Long companyId, Integer periodYear, Integer periodQuarter, ReportType reportType) {
    }

    private record PreparedValueImport(FinancialItemKey itemKey,
                                       String rawLabel,
                                       BigDecimal value,
                                       String currency,
                                       Integer unitMultiplier) {
    }

    private static final class PreparedReportImport {
        private final CompanyProfile company;
        private final Integer periodYear;
        private final Integer periodQuarter;
        private final ReportType reportType;
        private LocalDate publishedAt;
        private String sourceUrl;
        private final Map<FinancialItemKey, PreparedValueImport> items;
        private CompanyFinancialReport existingReport;
        private Map<String, CompanyFinancialValue> existingValues = Map.of();
        private Set<String> staleItemKeys = Set.of();

        private PreparedReportImport(CompanyProfile company,
                                     Integer periodYear,
                                     Integer periodQuarter,
                                     ReportType reportType,
                                     LocalDate publishedAt,
                                     String sourceUrl,
                                     Map<FinancialItemKey, PreparedValueImport> items) {
            this.company = company;
            this.periodYear = periodYear;
            this.periodQuarter = periodQuarter;
            this.reportType = reportType;
            this.publishedAt = publishedAt;
            this.sourceUrl = sourceUrl;
            this.items = items;
        }

        public CompanyProfile company() { return company; }
        public Integer periodYear() { return periodYear; }
        public Integer periodQuarter() { return periodQuarter; }
        public ReportType reportType() { return reportType; }
        public LocalDate publishedAt() { return publishedAt; }
        public String sourceUrl() { return sourceUrl; }
        public Map<FinancialItemKey, PreparedValueImport> items() { return items; }
        public CompanyFinancialReport existingReport() { return existingReport; }
        public Map<String, CompanyFinancialValue> existingValues() { return existingValues; }
        public Set<String> staleItemKeys() { return staleItemKeys; }
        public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public void setExistingReport(CompanyFinancialReport existingReport) { this.existingReport = existingReport; }
        public void setExistingValues(Map<String, CompanyFinancialValue> existingValues) { this.existingValues = existingValues; }
        public void setStaleItemKeys(Set<String> staleItemKeys) { this.staleItemKeys = staleItemKeys; }
    }

    private record ImportPreparation(List<PreparedReportImport> reportImports,
                                     int createdReports,
                                     int updatedReports,
                                     int createdValues,
                                     int updatedValues,
                                     int deletedStaleValues,
                                     List<String> affectedTickers) {
    }

    private static final class ImportCounters {
        private int createdReports;
        private int updatedReports;
        private int createdValues;
        private int updatedValues;
        private int deletedStaleValues;
    }
}



